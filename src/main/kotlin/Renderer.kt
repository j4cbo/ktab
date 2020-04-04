import kotlin.math.cos
import kotlin.math.sin

@ExperimentalUnsignedTypes
object Renderer {

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

    private val allOscillators = listOf(root, xOsc, yOsc, zOsc, redOsc, greenOsc, blueOsc, blankOsc)

    private val allControls= mapOf(
        "master" to root, "x" to xOsc, "y" to yOsc, "z" to zOsc,
        "red" to redOsc, "green" to greenOsc, "blue" to blueOsc, "blank" to blankOsc,
        "xrot" to xRot, "yrot" to yRot,
        "xwfm" to xOsc.waveform, "ywfm" to yOsc.waveform, "zwfm" to zOsc.waveform,
        "redwfm" to redOsc.waveform, "greenwfm" to greenOsc.waveform, "bluewfm" to blueOsc.waveform,
        "blankwfm" to blankOsc.waveform
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
    private fun Double.scaleForXY() = ((this - 0.5) * Short.MAX_VALUE.toDouble()).toInt().toShort()
    private fun DoubleControl.asRadians() = (this.value / 180) * kotlin.math.PI

    fun renderToPoint(point: EtherDreamPoint) {
        val blank = blankOsc.render()
        point.r = (redOsc.render() * blank).scaleForColor()
        point.g = (greenOsc.render() * blank).scaleForColor()
        point.b = (blueOsc.render() * blank).scaleForColor()

        val x = xOsc.render()
        val y = yOsc.render()
        val z = zOsc.render()

        if (x < 0 || x > 1) {
            throw Exception("what $x")
        }

        point.x = (x * cos(yRot.asRadians()) + (z * cos(
            xRot.asRadians()
        ) + y * sin(xRot.asRadians())) * sin(yRot.asRadians())).scaleForXY()
        point.y = (y * cos(xRot.asRadians()) - z * sin(
            xRot.asRadians()
        )).scaleForXY()

        allOscillators.forEach {
            it.advance()
        }
    }
}