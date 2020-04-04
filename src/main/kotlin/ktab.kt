import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.DefaultHeaders
import io.ktor.http.ContentType
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import io.ktor.http.content.files
import io.ktor.http.content.static
import io.ktor.response.respondFile
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import java.io.File
import java.lang.Thread.MAX_PRIORITY
import java.lang.Thread.sleep
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.sin

const val PPS = 30000

@ExperimentalUnsignedTypes
object Oscillators {

    private val master = Oscillator("master", 75.0)
    private val xOsc = OscillatorWithWaveform("x", 74.8)
    private val yOsc = OscillatorWithWaveform("y", 75.0)
    private val zOsc = OscillatorWithWaveform("z", 75.0)
    private val redOsc = OscillatorWithWaveform("r", 74.5)
    private val greenOsc = OscillatorWithWaveform("g", 75.0)
    private val blueOsc = OscillatorWithWaveform("b", 75.5)
    private val blankOsc = OscillatorWithWaveform("blank", 0.0)
    private val xRot = DoubleControl(0.0)
    private val yRot = DoubleControl(0.0)

    private val allOscillators = listOf(master, xOsc, yOsc, zOsc, redOsc, greenOsc, blueOsc, blankOsc)

    private val allControls= mapOf(
        "master" to master, "x" to xOsc, "y" to yOsc, "z" to zOsc,
        "r" to redOsc, "g" to greenOsc, "b" to blueOsc, "blank" to blankOsc,
        "xrot" to xRot, "yrot" to yRot,
        "xwfm" to xOsc.waveform, "ywfm" to yOsc.waveform, "zwfm" to zOsc.waveform,
        "rwfm" to redOsc.waveform, "gwfm" to greenOsc.waveform, "bwfm" to blueOsc.waveform,
        "blankwfm" to blankOsc.waveform
    )

    fun handleUpdates(keys: List<String>) {
        for (key in keys) {
            val parts = key.split(":")

            val control = allControls[parts[0]]
            if (control != null) {
                control.update(parts[1])
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

        val x = xOsc.render(Oscillator.Waveform.SIN)
        val y = yOsc.render(Oscillator.Waveform.SIN)
        val z = zOsc.render(Oscillator.Waveform.SIN)

        point.x = (x * cos(yRot.asRadians()) + (z * cos(xRot.asRadians()) + y * sin(xRot.asRadians())) * sin(yRot.asRadians())).scaleForXY()
        point.y = (y * cos(xRot.asRadians()) - z * sin(xRot.asRadians())).scaleForXY()

        allOscillators.forEach {
            it.advance()
        }
    }
}


fun Application.module() {
    install(DefaultHeaders)
    install(CallLogging)
    install(WebSockets)
    install(Routing) {
        static("static") {
            files("static")
        }
        get("/foo") {
            call.respondText("Hello World", ContentType.Text.Html)
        }
        get("/") {
            call.respondFile(File("static/index.html"))
        }
        webSocket("/websocket") {
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        Oscillators.handleUpdates(text.split(" "))
                        call.application.environment.log.info(text)
                    }
                }
            }
        }
    }
}

@Structure.FieldOrder("x", "y", "r", "g", "b", "i", "u1", "u2")
class EtherDreamPoint(
    @JvmField var x: Short,
    @JvmField var y: Short,
    @JvmField var r: UShort,
    @JvmField var g: UShort,
    @JvmField var b: UShort,
    @JvmField var i: UShort,
    @JvmField var u1: UShort,
    @JvmField var u2: UShort
) : Structure() {
    constructor() : this(0, 0, 0U, 0U, 0U, 0U, 0U, 0U)
}

@Suppress("FunctionName")
interface EtherDreamLib : Library {
    fun etherdream_lib_start(): Int
    fun etherdream_dac_count(): Int
    fun etherdream_get(index: Int): Pointer
    fun etherdream_get_id(dac: Pointer): UInt
    fun etherdream_connect(dac: Pointer): Int
    fun etherdream_is_ready(dac: Pointer): Int
    fun etherdream_wait_for_ready(dac: Pointer): Int
    fun etherdream_write(dac: Pointer, points: Array<Structure>, num: Int, pps: Int, repeatCount: Int): Int
    fun etherdream_stop(dac: Pointer): Int
}

fun producerThread(lib: EtherDreamLib, dac: Pointer) {
    while (true) {
        val waitReturn = lib.etherdream_wait_for_ready(dac)
        if (waitReturn != 0) {
            println("wait returned $waitReturn")
            return
        }

        val arr = EtherDreamPoint().toArray(1000)
        for (point in arr) {
            Oscillators.renderToPoint(point as EtherDreamPoint)
        }

        val writeReturn = lib.etherdream_write(dac, arr, arr.size, PPS, 1)
        if (writeReturn != 0) {
            println("write returned $writeReturn")
            return
        }
    }

}

fun main(args: Array<String>) {

    val lib = Native.load("/Users/jacob/Code/j4cDAC/driver/libetherdream/etherdream.dylib", EtherDreamLib::class.java)
    lib.etherdream_lib_start()
    sleep(1000)
    println("${lib.etherdream_dac_count()}")

    val dac = lib.etherdream_get(0)
    println("connect returned ${lib.etherdream_connect(dac)}")
    
    thread(isDaemon = true, priority = MAX_PRIORITY) {
        producerThread(lib, dac)
    }

    embeddedServer(Netty, 8080, watchPaths = listOf("BlogAppKt"), module = Application::module).start()
}