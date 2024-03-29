import java.lang.Math.cbrt
import kotlin.math.sin

@ExperimentalUnsignedTypes
fun UInt.toFullRangeRadians() = toDouble() * Math.PI * 2.0 / UInt.MAX_VALUE.toDouble()

@ExperimentalUnsignedTypes
open class Oscillator(override val name: String, var freq: Double, var state: UInt = 0U): Control() {

    enum class Waveform { SIN, SINR3, PLUS_TRI, MINUS_TRI, SQ10, SQ25, SQ50, SQ75, SQ90 }

    fun renderUnipolar(waveform: Waveform) =
        renderImpl(waveform).also { check(it in 0.0..1.0) }

    fun renderBipolar(waveform: Waveform) =
        (renderImpl(waveform).also { check(it in 0.0..1.0) } - 0.5) * 2.0

    private fun renderImpl(waveform: Waveform) =
        if (freq == 0.0)
            1.0
        else
            when (waveform) {
                Waveform.SIN -> sin(state.toFullRangeRadians()) / 2.0 + 0.5
                Waveform.SINR3 -> {
                    val a = sin(state.toFullRangeRadians())
                    (if (a > 0) cbrt(a) else -cbrt(-a)) / 2.0 + 0.5
                }
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

    override fun update(parts: List<String>) {
        freq = parts[0].toDouble()
    }
}

@ExperimentalUnsignedTypes
open class OscillatorPlus(
    name: String,
    freq: Double,
    private val parent: Oscillator,
    state: UInt = 0U,
    waveform: Waveform = Waveform.SIN
) : Oscillator(name, freq, state) {
    val waveform = makeEnumControl(name + "Waveform", waveform)

    open fun renderUnipolar() = renderUnipolar(waveform.value)

    open fun renderBipolar() = renderBipolar(waveform.value)

    override fun update(parts: List<String>) {
        if (parts[0] == "phase") {
            val multiplierIndex = parts[1].toInt()
            val offset = parts[2].toDouble()
            freq = parent.freq * multipliers[multiplierIndex]
            state = ((parent.state.toDouble() * multipliers[multiplierIndex]).toLong() + (offset * degreesToValue).toLong()).toULong().toUInt()
        } else {
            super.update(parts)
        }
    }

    companion object {
        private val multipliers = listOf(0.25, 1/3.0, 0.5, 1.0, 1.5, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0)
        private val degreesToValue = (UInt.MAX_VALUE.toDouble() / 360.0)
    }
}

@ExperimentalUnsignedTypes
class OscillatorAbsolute(
    name: String,
    freq: Double,
    parent: Oscillator,
    state: UInt = 0U,
    waveform: Waveform = Waveform.SIN,
    private var absoluteValue: Double? = null
) : OscillatorPlus(name, freq, parent, state, waveform) {

    override fun renderUnipolar() = absoluteValue ?: super.renderUnipolar()

    // renderBipolar is for X/Y values - don't want to use a constant for those!
    override fun renderBipolar() = throw NotImplementedError("do not use renderBipolar on oscillatorAbsolute")

    override fun update(parts: List<String>) {
        if (parts[0] == "absolute") {
            absoluteValue = parts[1].toDouble()
        } else {
            absoluteValue = null
            super.update(parts)
        }
    }
}
