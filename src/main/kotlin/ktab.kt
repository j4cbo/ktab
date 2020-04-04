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

class WaveformControl(var waveform: Oscillator.Waveform = Oscillator.Waveform.SIN)

@ExperimentalUnsignedTypes
object Oscillators {

    val master = Oscillator("master", 75.0)
    val x = Oscillator("x", 74.8)
    val y = Oscillator("y", 75.0)
    val z = Oscillator("z", 75.0)
    val r = Oscillator("r", 74.5)
    val g = Oscillator("g", 75.0)
    val b = Oscillator("b", 75.5)
    val blank = Oscillator("blank", 0.0)

    val rWaveform = WaveformControl()
    val gWaveform = WaveformControl()
    val bWaveform = WaveformControl()
    val blankWaveform = WaveformControl()

    val allOscillators = mapOf(
        "master" to master,
        "x" to x,
        "y" to y,
        "z" to z,
        "r" to r,
        "g" to g,
        "b" to b,
        "blank" to blank
    )

    fun handleUpdates(keys: List<String>) {
        for (key in keys) {
            val parts = key.split(":")
            val oscillator = allOscillators[parts[0]]
            if (oscillator != null) {
                oscillator.freq = parts[1].toDouble()
            }
        }
    }

    private fun Double.scaleForColor() = (this * UShort.MAX_VALUE.toDouble()).toInt().toUShort()
    fun Double.scaleForXY() = ((this - 0.5) * Short.MAX_VALUE.toDouble()).toInt().toShort()

    fun renderToPoint(point: EtherDreamPoint) {
        point.r = (r.render(rWaveform.waveform) * blank.render(blankWaveform.waveform)).scaleForColor()
        point.g = (g.render(gWaveform.waveform) * blank.render(blankWaveform.waveform)).scaleForColor()
        point.b = (b.render(bWaveform.waveform) * blank.render(blankWaveform.waveform)).scaleForColor()

        val intX = x.render(Oscillator.Waveform.SIN)
        val intY = y.render(Oscillator.Waveform.SIN)
        val intZ = z.render(Oscillator.Waveform.SIN)

        val xRot = 0.0
        val yRot = 0.0

        point.x = (intX * cos(yRot) + (intZ * cos(xRot) + intY * sin(xRot)) * sin(yRot)).scaleForXY()
        point.y = (intY * cos(xRot) - intZ * sin(xRot)).scaleForXY()
    }
}

@ExperimentalUnsignedTypes
fun advanceOscillators() = Oscillators.allOscillators.values.forEach { it.advance() }

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
            advanceOscillators()
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