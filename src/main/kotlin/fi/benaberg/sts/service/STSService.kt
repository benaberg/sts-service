package fi.benaberg.sts.service

import fi.benaberg.sts.service.handlers.HttpServletHandler
import fi.benaberg.sts.service.handlers.StorageHandler
import fi.benaberg.sts.service.handlers.WsServletHandler
import fi.benaberg.sts.service.util.PropertiesUtil
import org.java_websocket.WebSocket
import java.util.*
import kotlin.concurrent.thread
import kotlin.io.path.Path

/**
 * Main service initialization.
 */
fun main() {

    val sessions = Collections.synchronizedSet(mutableSetOf<WebSocket>())
    val log = LogRef(sessions)

    log.write("Starting STS Service...")

    // Servlet ports and context paths
    val propertiesUtil = PropertiesUtil()
    val httpPort = propertiesUtil.getProperty("service.port.http").toInt()
    val wsPort = propertiesUtil.getProperty("service.port.ws").toInt()
    val temperatureContext = propertiesUtil.getProperty("service.context.temperature")
    val dashboardContext = propertiesUtil.getProperty("service.context.dashboard")

    // Data directory resolved relative to working location
    val dataDir = propertiesUtil.getProperty("service.dir.data")

    // Setup storage handler
    val storageHandler = StorageHandler(log, Path(dataDir))

    // Start HTTP server
    thread {
        val httpServletHandler = HttpServletHandler(log, httpPort, wsPort, temperatureContext, dashboardContext, storageHandler)
        httpServletHandler.start()
    }

    // Start dashboard (WS) server
    thread {
        // Start server
        val wsServletHandler = WsServletHandler(log, wsPort, storageHandler, sessions)
        wsServletHandler.start()
    }
}