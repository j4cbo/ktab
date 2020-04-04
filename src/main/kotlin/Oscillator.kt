import kotlin.math.sin

@ExperimentalUnsignedTypes
class Oscillator(val name: String, var freq: Double, var state: ULong = 0U) {

    enum class Waveform { SIN, PLUS_TRI, MINUS_TRI, SQ10, SQ25, SQ50, SQ75, SQ90 }

    fun render(waveform: Waveform) =
        if (freq == 0.0)
            1.0
        else
            when (waveform) {
                Waveform.SIN -> sin((state.toDouble() / UInt.MAX_VALUE.toDouble()) * Math.PI * 2.0) / 2.0 + 0.5
                Waveform.PLUS_TRI -> state.toDouble() / UInt.MAX_VALUE.toDouble()
                Waveform.MINUS_TRI -> 1 - (state.toDouble() / UInt.MAX_VALUE.toDouble())
                Waveform.SQ10 -> if (state < (UInt.MAX_VALUE / 10U)) 1.0 else 0.0
                Waveform.SQ25 -> if (state < (UInt.MAX_VALUE / 4U)) 1.0 else 0.0
                Waveform.SQ50 -> if (state < (UInt.MAX_VALUE / 2U)) 1.0 else 0.0
                Waveform.SQ75 -> if (state < 3U * (UInt.MAX_VALUE / 4U)) 1.0 else 0.0
                Waveform.SQ90 -> if (state < 9U * (UInt.MAX_VALUE / 10U)) 1.0 else 0.0
            }

    fun advance() {
        state += ((UInt.MAX_VALUE / PPS.toUInt()).toDouble() * freq).toUInt()
    }
}