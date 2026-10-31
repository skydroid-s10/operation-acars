package uk.co.operationacars

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Process
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.graphics.Typeface
import android.view.ViewGroup
import android.app.Activity
import kotlin.concurrent.thread
import kotlin.math.sqrt
import uk.co.operationacars.decoder.AcarsDecodePipeline
import uk.co.operationacars.decoder.AudioFilePcmDecoder

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var level: TextView
    private lateinit var messages: TextView
    private lateinit var diagnostics: TextView
    private lateinit var diagPanel: LinearLayout
    private var recorder: AudioRecord? = null
    @Volatile private var running = false
    @Volatile private var lastRms = 0.0
    @Volatile private var samplesRead = 0L
    @Volatile private var inputError = "Not started"
    @Volatile private var demodulatedBits = 0L
    private var decoderPipeline: AcarsDecodePipeline? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 42)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 20)
            setBackgroundColor(0xFFF3F6FA.toInt())
        }
        root.addView(label("OPERATION ACARS", 25, true))
        root.addView(label("VHF audio decoder • prototype 0.1", 14, false))
        status = label("Status: stopped", 16, true)
        root.addView(status)
        level = label("Audio input: waiting", 15, false)
        root.addView(level)

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(button("START") { startCapture() }, weightParams())
        buttons.addView(button("STOP") { stopCapture() }, weightParams())
        buttons.addView(button("TEST AUDIO FILE") { chooseAudioFile() }, weightParams())
        buttons.addView(button("DIAGNOSTICS") { showDiagnostics() }, weightParams())
        root.addView(buttons)

        messages = label(
            "ACARS TEST CONSOLE\n\nChoose TEST AUDIO FILE to analyse a saved recording, or START for live audio.\n\nThis build reports experimental demodulator activity only; it does not yet validate or display ACARS messages.",
            15, false
        )
        val scroll = ScrollView(this)
        scroll.addView(messages)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        diagPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
            setPadding(0, 12, 0, 0)
        }
        diagnostics = label("", 14, false)
        diagPanel.addView(diagnostics)
        diagPanel.addView(button("BACK TO MESSAGES") {
            diagPanel.visibility = android.view.View.GONE
            messages.visibility = android.view.View.VISIBLE
        })
        root.addView(diagPanel)
        setContentView(root)
    }

    private fun label(text: String, size: Int, bold: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size.toFloat()
            setTextColor(0xFF17212B.toInt())
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 8, 0, 8)
        }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 11f
        setOnClickListener { action() }
    }

    private fun weightParams() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    private fun showDiagnostics() {
        messages.visibility = android.view.View.GONE
        diagPanel.visibility = android.view.View.VISIBLE
        diagnostics.text = listOf(
            "ADVANCED DIAGNOSTICS",
            "",
            "Audio capture: ${if (running) "RUNNING" else "STOPPED"}",
            "Sample rate requested: 48,000 Hz",
            "Input samples read: $samplesRead",
            "MSK bits demodulated: $demodulatedBits",
            "Frame/character validation: not yet enabled",
            "Approx. RMS level: ${"%.5f".format(lastRms)}",
            "Input state: $inputError",
            "Offline test: select TEST AUDIO FILE to analyse a recording",
            "",
            "Decoder synchronisation: NOT IMPLEMENTED",
            "Valid ACARS frames: N/A",
            "Rejected ACARS frames: N/A",
            "Checksum statistics: N/A",
            "",
            "This prototype does not yet decode ACARS. A proper MSK demodulator and frame decoder must be integrated and verified against reference recordings."
        ).joinToString("\n")
    }

    private fun chooseAudioFile() {
        if (running) stopCapture()
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        startActivityForResult(intent, 73)
    }

    @Deprecated("Using the platform document picker for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 73 || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        status.text = "Status: decoding selected audio file…"
        level.text = "Offline test: reading recording"
        messages.text = "AUDIO FILE TEST\n\nDecoding audio and running the experimental 2400-baud MSK front-end…"
        thread(name = "AcarsOfflineAudioTest") {
            try {
                val decoded = AudioFilePcmDecoder.decodeToMono48k(this, uri)
                val pipeline = AcarsDecodePipeline { _ -> }
                var sumSquares = 0.0
                var peak = 0
                for (sample in decoded.samples) {
                    val v = sample.toInt()
                    sumSquares += (v / 32768.0) * (v / 32768.0)
                    peak = maxOf(peak, kotlin.math.abs(v))
                }
                var offset = 0
                while (offset < decoded.samples.size) {
                    val count = minOf(2048, decoded.samples.size - offset)
                    pipeline.processPcm16(decoded.samples.copyOfRange(offset, offset + count), count)
                    offset += count
                }
                val duration = decoded.samples.size / 48000.0
                val rms = kotlin.math.sqrt(sumSquares / decoded.samples.size)
                runOnUiThread {
                    status.text = "Status: offline audio test complete"
                    level.text = "File audio RMS: ${"%.4f".format(rms)}   Duration: ${"%.1f".format(duration)} s"
                    messages.text = listOf(
                        "OFFLINE AUDIO TEST RESULT",
                        "",
                        "File: ${uri.lastPathSegment ?: "selected audio"}",
                        "Decoded audio: ${decoded.sampleRate} Hz source output, ${decoded.channels} channel(s)",
                        "Test input: mono PCM16, 48,000 Hz",
                        "Duration: ${"%.2f".format(duration)} seconds",
                        "Peak level: $peak / 32767",
                        "RMS level: ${"%.5f".format(rms)}",
                        "MSK front-end bits: ${pipeline.bitsProduced}",
                        "",
                        "IMPORTANT: bit production only confirms that the experimental signal-processing stage ran. It does NOT mean an ACARS frame or message was decoded. Sync, parity, BCS and message parsing remain to be implemented and validated."
                    ).joinToString("\n")
                    samplesRead = decoded.samples.size.toLong()
                    demodulatedBits = pipeline.bitsProduced
                    lastRms = rms
                    inputError = "Offline file test completed; ACARS validation not yet implemented"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Status: offline test failed"
                    level.text = "Audio file could not be processed"
                    messages.text = "AUDIO TEST ERROR\n\n${e.message ?: e.javaClass.simpleName}"
                }
            }
        }
    }

    private fun startCapture() {
        if (running) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 42)
            return
        }
        val rate = 48000
        val minBuffer = AudioRecord.getMinBufferSize(
            rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            inputError = "AudioRecord buffer unavailable ($minBuffer)"
            status.text = "Status: audio input unavailable"
            Toast.makeText(this, inputError, Toast.LENGTH_LONG).show()
            return
        }
        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer * 2, 8192)
            )
            decoderPipeline = AcarsDecodePipeline { _ -> demodulatedBits++ }
            demodulatedBits = 0
            samplesRead = 0
            recorder?.startRecording()
            running = true
            inputError = "Capturing mono PCM; MSK front-end active"
            status.text = "Status: capturing audio (MSK bitstream active; ACARS validation pending)"
            thread(name = "AcarsAudioCapture") {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                val buffer = ShortArray(2048)
                while (running) {
                    val n = recorder?.read(buffer, 0, buffer.size) ?: -1
                    if (n > 0) {
                        var sum = 0.0
                        for (i in 0 until n) {
                            val v = buffer[i].toDouble() / 32768.0
                            sum += v * v
                        }
                        lastRms = sqrt(sum / n)
                        samplesRead += n
                        decoderPipeline?.processPcm16(buffer, n)
                        runOnUiThread {
                            level.text = "Audio RMS: ${"%.4f".format(lastRms)}   Samples: $samplesRead"
                        }
                    } else if (n < 0) {
                        inputError = "AudioRecord read error: $n"
                        running = false
                    }
                }
            }
        } catch (e: Exception) {
            running = false
            inputError = e.message ?: "Could not start audio capture"
            status.text = "Status: audio capture failed"
            Toast.makeText(this, inputError, Toast.LENGTH_LONG).show()
        }
    }

    private fun stopCapture() {
        running = false
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release()
        recorder = null
        decoderPipeline = null
        status.text = "Status: stopped"
        inputError = "Stopped"
        level.text = "Audio input: stopped"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission granted. Press START.", Toast.LENGTH_SHORT).show()
        } else if (requestCode == 42) {
            Toast.makeText(this, "Microphone permission is required for receiver audio.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }
}
