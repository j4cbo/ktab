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
import java.lang.Thread.sleep
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.sin

const val PPS = 30000

enum class Waveform { SIN, PLUS_TRI, MINUS_TRI, SQ10, SQ25, SQ50, SQ75, SQ90 }

class WaveformControl(var waveform: Waveform = Waveform.SIN)

@ExperimentalUnsignedTypes
object Oscillators {

    data class Oscillator(val name: String, var freq: Double, var state: ULong = 0U) {
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
    }

    val master = Oscillator("master", 100.0)
    val x = Oscillator("x", 99.8)
    val y = Oscillator("x", 100.0)
    val z = Oscillator("x", 100.0)
    val r = Oscillator("x", 99.5)
    val g = Oscillator("x", 100.0)
    val b = Oscillator("x", 100.5)
    val blank = Oscillator("x", 0.0)

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

    private fun Double.scaleForColor() = (this * UShort.MAX_VALUE.toDouble()).toInt().toUShort()
    fun Double.scaleForXY() = (this * Short.MAX_VALUE.toDouble() / 3.0).toInt().toShort()

    fun render(): EtherDreamPoint {
        val outRed = (r.render(rWaveform.waveform) * blank.render(blankWaveform.waveform)).scaleForColor()
        val outGreen = (g.render(gWaveform.waveform) * blank.render(blankWaveform.waveform)).scaleForColor()
        val outBlue = (b.render(bWaveform.waveform) * blank.render(blankWaveform.waveform)).scaleForColor()

        val intX = x.render(Waveform.SIN)
        val intY = y.render(Waveform.SIN)
        val intZ = z.render(Waveform.SIN)

        val xRot = 0.0
        val yRot = 0.0

        val outX = (intX * cos(yRot) + (intZ * cos(xRot) + intY * sin(xRot)) * sin(yRot)).scaleForXY()
        val outY = (intY * cos(xRot) - intZ * sin(xRot)).scaleForXY()

        return EtherDreamPoint(outX, outY, outRed, outGreen, outBlue, 0U, 0U, 0U)
    }
}

@ExperimentalUnsignedTypes
fun advanceOscillators() = Oscillators.allOscillators.values.forEach {
    it.state += ((UInt.MAX_VALUE / PPS.toUInt()).toDouble() * it.freq).toUInt()
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

        val points = (0..1000).map {
            advanceOscillators()
            Oscillators.render()
        }.toTypedArray()

        val writeReturn = lib.etherdream_write(dac, EtherDreamPoint().toArray(points), points.size, PPS, 1)
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

    thread(isDaemon = true) {
        producerThread(lib, dac)
    }

    embeddedServer(Netty, 8080, watchPaths = listOf("BlogAppKt"), module = Application::module).start()
}