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

private const val FRAME = 1000

fun producerThread(dac: Pointer) {
    val buffer = ShortArray(FRAME * 8) { 0 }
    while (true) {
        val waitReturn = EtherDream.etherdream_wait_for_ready(dac)
        if (waitReturn != 0) {
            println("wait returned $waitReturn")
            return
        }

        for (i in 0 until FRAME) {
            Renderer.renderToBuffer(buffer, i)
        }

        val writeReturn = EtherDream.etherdream_write(dac, buffer, FRAME, PPS, 1)
        if (writeReturn != 0) {
            println("write returned $writeReturn")
            return
        }
    }

}

fun main(args: Array<String>) {

    EtherDream.etherdream_lib_start()
    sleep(1000)

    val dacCount = EtherDream.etherdream_dac_count()
    println("Found $dacCount DACs")
    if (dacCount > 0) {
        val dac = EtherDream.etherdream_get(0)
        println("connect returned ${EtherDream.etherdream_connect(dac)}")

        thread(isDaemon = true, priority = MAX_PRIORITY) {
            producerThread(dac)
        }
    }

    embeddedServer(Netty, 8080, watchPaths = listOf("BlogAppKt"), module = Application::module).start()
}