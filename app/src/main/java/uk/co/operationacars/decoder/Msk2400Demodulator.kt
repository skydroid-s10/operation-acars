package uk.co.operationacars.decoder

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Streaming audio front-end for 2400-baud ACARS MSK.
 *
 * Input is signed PCM16 mono at 48 kHz (the app's AudioRecord format).
 * The signal is mixed to complex baseband at 1800 Hz, then a phase
 * discriminator estimates the sign of the MSK frequency deviation.
 * Five 12 kHz discriminator samples are integrated per 2400-baud symbol.
 *
 * This class deliberately emits raw demodulated bits only. ACARS preamble,
 * byte alignment, parity and BCS validation are separate stages; a bit is
 * NOT an accepted ACARS character or message until those stages pass.
 */
class Msk2400Demodulator(
    private val inputSampleRate: Int = 48_000,
    private val baudRate: Int = 2_400,
    private val centreFrequencyHz: Double = 1_800.0,
    private val decimation: Int = 4,
    private val onBit: (SoftBit) -> Unit
) {
    data class SoftBit(val value: Int, val confidence: Double)

    private val intermediateRate = inputSampleRate / decimation
    private val samplesPerSymbol = intermediateRate.toDouble() / baudRate
    private var inputIndex = 0
    private var decimationSum = 0.0
    private var decimationCount = 0
    private var phase = 0.0
    private var previousI = 0.0
    private var previousQ = 0.0
    private var havePrevious = false
    private var symbolAccumulator = 0.0
    private var symbolWeight = 0.0
    private var symbolPosition = 0.0

    init {
        require(inputSampleRate % decimation == 0) { "Sample rate must divide evenly by decimation" }
        require(intermediateRate % baudRate == 0) { "Intermediate sample rate must be an integer multiple of baud rate" }
        require(baudRate == 2_400) { "This front-end is configured for 2400-baud ACARS MSK" }
    }

    /** Feed any-sized PCM block. State is retained between calls. */
    fun process(samples: ShortArray, length: Int = samples.size) {
        require(length in 0..samples.size)
        for (n in 0 until length) {
            decimationSum += samples[n].toDouble() / 32768.0
            decimationCount++
            inputIndex++
            if (decimationCount == decimation) {
                val audio = decimationSum / decimationCount
                decimationSum = 0.0
                decimationCount = 0
                processIntermediate(audio)
            }
        }
    }

    private fun processIntermediate(audio: Double) {
        val step = 2.0 * PI * centreFrequencyHz / intermediateRate
        val i = audio * cos(phase)
        val q = -audio * sin(phase)
        phase += step
        if (phase >= 2.0 * PI) phase -= 2.0 * PI

        if (havePrevious) {
            // Imaginary part of current * conjugate(previous) is proportional
            // to instantaneous frequency offset around the 1800 Hz centre.
            val cross = previousI * q - previousQ * i
            val dot = previousI * i + previousQ * q
            val discriminator = kotlin.math.atan2(cross, dot)
            symbolAccumulator += discriminator
            symbolWeight += kotlin.math.abs(discriminator)
            symbolPosition += 1.0
            if (symbolPosition >= samplesPerSymbol) {
                val average = symbolAccumulator / symbolPosition
                val confidence = if (symbolWeight == 0.0) 0.0 else
                    (kotlin.math.abs(symbolAccumulator) / symbolWeight).coerceIn(0.0, 1.0)
                onBit(SoftBit(if (average >= 0.0) 1 else 0, confidence))
                symbolAccumulator = 0.0
                symbolWeight = 0.0
                symbolPosition -= samplesPerSymbol
            }
        }
        previousI = i
        previousQ = q
        havePrevious = true
    }

    fun reset() {
        inputIndex = 0
        decimationSum = 0.0
        decimationCount = 0
        phase = 0.0
        previousI = 0.0
        previousQ = 0.0
        havePrevious = false
        symbolAccumulator = 0.0
        symbolWeight = 0.0
        symbolPosition = 0.0
    }
}
