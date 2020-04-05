import kotlin.math.cos
import kotlin.math.sin

@ExperimentalUnsignedTypes
object Renderer {

    enum class Mode { MODE1, MODE2, MODE3, MODE4, MODE5, MODE6 }

    private val root = Oscillator("master", 75.0)
    private val xOsc = OscillatorPlus("x", 74.8, root)
    private val yOsc = OscillatorPlus("y", 75.0, root)
    private val zOsc = OscillatorPlus("z", 75.0, root)
    private val redOsc = OscillatorAbsolute("r", 74.5, root)
    private val greenOsc = OscillatorAbsolute("g", 75.0, root)
    private val blueOsc = OscillatorAbsolute("b", 75.5, root)
    private val blankOsc = OscillatorAbsolute("blank", 0.0, root, absoluteValue = 1.0)
    private val xRot = DoubleControl("xRot", 0.0)
    private val yRot = DoubleControl("yRot", 0.0)
    private val mode = makeEnumControl("mode", Mode.MODE1)

    private val allOscillators = listOf(root, xOsc, yOsc, zOsc, redOsc, greenOsc, blueOsc, blankOsc)

    private val allControls= mapOf(
        "master" to root, "x" to xOsc, "y" to yOsc, "z" to zOsc,
        "red" to redOsc, "green" to greenOsc, "blue" to blueOsc, "blank" to blankOsc,
        "xrot" to xRot, "yrot" to yRot,
        "xwfm" to xOsc.waveform, "ywfm" to yOsc.waveform, "zwfm" to zOsc.waveform,
        "redwfm" to redOsc.waveform, "greenwfm" to greenOsc.waveform, "bluewfm" to blueOsc.waveform,
        "blankwfm" to blankOsc.waveform, "mode" to mode
    )

    fun handleUpdates(keys: List<String>) {
        for (item in keys) {
            val parts = item.split(":")
            val control = allControls[parts[0]]
            if (control != null) {
                control.update(parts.drop(1))
            } else {
                println("Unknown control ${parts[0]}")
            }
        }
    }

    private fun Double.scaleForColor() = (this * UShort.MAX_VALUE.toDouble()).toInt().toUShort()
    private fun Double.scaleForXY() = (this * Short.MAX_VALUE.toDouble()).toInt().toShort()
    private fun DoubleControl.asRadians() = (this.value / 180) * kotlin.math.PI

    fun renderToBuffer(buffer: ShortArray, offset: Int) {
        val blank = blankOsc.render()
        val r = (redOsc.render() * blank).scaleForColor()
        val g = (greenOsc.render() * blank).scaleForColor()
        val b = (blueOsc.render() * blank).scaleForColor()

        val x: Double
        val y: Double
        val z: Double

        when (mode.value) {
            Mode.MODE1 -> {
                x = xOsc.render()
                y = yOsc.render()
                z = zOsc.render()
            }
            Mode.MODE2 -> {
                val multiplier = zOsc.render() - 0.5
                x = xOsc.render() * multiplier
                y = yOsc.render() * multiplier
                z = 0.0
            }
            else -> {
                x = xOsc.render()
                y = yOsc.render()
                z = zOsc.render()
            }
        }

        val x1 = x - 0.5
        val y1 = y - 0.5
        val z1 = z - 0.5

        val xo = x1 * cos(yRot.asRadians()) + (z1 * cos(xRot.asRadians()) + y1 * sin(xRot.asRadians())) * sin(yRot.asRadians())
        val yo = y1 * cos(xRot.asRadians()) - z1 * sin(xRot.asRadians())

        buffer[offset * 8 + 0] = (xo * 0.45).scaleForXY()
        buffer[offset * 8 + 1] = (yo * 0.45 + 0.48).scaleForXY()
        buffer[offset * 8 + 2] = r.toShort()
        buffer[offset * 8 + 3] = g.toShort()
        buffer[offset * 8 + 4] = b.toShort()

        allOscillators.forEach {
            it.advance()
        }
    }
}