package fi.benaberg.sts.service.model

data class TemperatureReading(var sensorId: Int, var sensorName: String, var temperature: Int, var timestamp: Long)
