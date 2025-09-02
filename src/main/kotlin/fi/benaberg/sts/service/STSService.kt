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
    val sensorContext = propertiesUtil.getProperty("service.context.sensors")

    // Data directories resolved relative to working location
    val applicationDataDir = propertiesUtil.getProperty("service.dir.data.application")
    val ltsDataDir = propertiesUtil.getProperty("service.dir.data.lts")

    // Setup storage handler
    val storageHandler = StorageHandler(log, Path(applicationDataDir), Path(ltsDataDir))

    // Start HTTP server
    thread {
        val httpServletHandler = HttpServletHandler(log, httpPort, wsPort, temperatureContext, dashboardContext, sensorContext, storageHandler)
        httpServletHandler.start()
    }

    // Start dashboard (WS) server
    thread {
        // Start server
        val wsServletHandler = WsServletHandler(log, wsPort, storageHandler, sessions)
        wsServletHandler.start()
    }
}