package fi.benaberg.sts.service

import java.io.File
import java.io.FileInputStream
import java.util.Properties

private const val PROPERTIES = "service.properties"

class PropertiesUtil {

    private val properties = Properties()

    init {
        val fis = FileInputStream(File(PROPERTIES))
        properties.load(fis)
    }

    fun getProperty(key: String): String {
        return properties.getProperty(key)
    }
}