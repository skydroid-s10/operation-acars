package uk.co.operationacars.decoder

import java.util.ArrayDeque

/**
 * Bit-stream synchronisation utility. It accepts a caller-supplied sync word
 * rather than hard-coding an unverified framing assumption. The next stage
 * will provide the documented ACARS preamble/unique-word pattern and byte
 * polarity handling after comparison with the reference decoder.
 */
class AcarsFrameSynchronizer(
    private val syncWord: IntArray,
    private val maxBitErrors: Int = 1,
    private val onSync: (bitOffset: Long, errors: Int) -> Unit
) {
    private val window = ArrayDeque<Int>()
    private var offset = 0L

    init {
        require(syncWord.isNotEmpty())
        require(syncWord.all { it == 0 || it == 1 })
        require(maxBitErrors in 0..syncWord.size)
    }

    fun accept(bit: Int) {
        require(bit == 0 || bit == 1)
        window.addLast(bit)
        if (window.size > syncWord.size) window.removeFirst()
        if (window.size == syncWord.size) {
            var errors = 0
            var i = 0
            for (observed in window) {
                if (observed != syncWord[i]) errors++
                i++
            }
            if (errors <= maxBitErrors) onSync(offset - syncWord.size + 1, errors)
        }
        offset++
    }

    fun reset() {
        window.clear()
        offset = 0L
    }
}
