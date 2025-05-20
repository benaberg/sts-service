package fi.benaberg.sts

fun main() {
    val propertiesUtil = PropertiesUtil()
    val port = propertiesUtil.getProperty("local.port").toInt()
    println(port)
}