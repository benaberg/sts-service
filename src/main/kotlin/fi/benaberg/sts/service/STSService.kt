package fi.benaberg.sts.service

import fi.benaberg.sts.service.handlers.ServletHandler
import fi.benaberg.sts.service.handlers.StorageHandler
import fi.benaberg.sts.service.util.PropertiesUtil
import kotlin.io.path.Path

/**
 * Main service initialization.
 */
fun main() {

    val propertiesUtil = PropertiesUtil()

    // Servlet port and context path
    val port = propertiesUtil.getProperty("service.port").toInt()
    val context = propertiesUtil.getProperty("service.context.temperature")

    // Data directory resolved relative to working location
    val dataDir = propertiesUtil.getProperty("service.dir.data")

    // Setup storage handler
    val storageHandler = StorageHandler(Path(dataDir))
    
    // Start server
    val servletHandler = ServletHandler(port, context, storageHandler)
    servletHandler.start()

}