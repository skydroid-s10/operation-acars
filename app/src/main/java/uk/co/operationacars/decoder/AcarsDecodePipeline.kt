package uk.co.operationacars.decoder

/**
 * Coordinator used by AudioRecord. Currently exposes demodulated-bit
 * diagnostics; accepted ACARS messages remain disabled until framing, parity
 * and BCS validation are implemented and tested against reference vectors.
 */
class AcarsDecodePipeline(
    private val onDemodulatedBit: (Msk2400Demodulator.SoftBit) -> Unit
) {
    var bitsProduced: Long = 0
        private set

    val demodulator = Msk2400Demodulator(onBit = { bit ->
        bitsProduced++
        onDemodulatedBit(bit)
    })

    fun processPcm16(samples: ShortArray, length: Int = samples.size) {
        demodulator.process(samples, length)
    }

    fun reset() {
        demodulator.reset()
        bitsProduced = 0
    }
}
