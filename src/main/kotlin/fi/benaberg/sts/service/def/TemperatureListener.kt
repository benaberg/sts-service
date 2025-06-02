package fi.benaberg.sts.service.def

import fi.benaberg.sts.service.model.TemperatureReading

fun interface TemperatureListener {

    fun onReadingAdded(temperatureReading: TemperatureReading)

}