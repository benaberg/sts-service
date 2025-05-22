package fi.benaberg.sts

fun main() {

    val propertiesUtil = PropertiesUtil()
    val port = propertiesUtil.getProperty("service.port").toInt()
    val context = propertiesUtil.getProperty("service.temperature.context")
    val handler = ServletHandler(port, context)

    // Start server
    handler.start()

}