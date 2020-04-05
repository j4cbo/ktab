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

const val PPS = 30000

@ExperimentalUnsignedTypes
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
                        Renderer.handleUpdates(text.split(" "))
                        call.application.environment.log.info(text)
                    }
                }
            }
        }
    }
}

@ExperimentalUnsignedTypes
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
            Renderer.renderToPoint(point as EtherDreamPoint)
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

    val dacCount = lib.etherdream_dac_count()
    println("Found $dacCount DACs")
    if (dacCount > 0) {
        val dac = lib.etherdream_get(0)
        println("connect returned ${lib.etherdream_connect(dac)}")

        thread(isDaemon = true, priority = MAX_PRIORITY) {
            producerThread(lib, dac)
        }
    }

    embeddedServer(Netty, 8080, watchPaths = listOf("BlogAppKt"), module = Application::module).start()
}