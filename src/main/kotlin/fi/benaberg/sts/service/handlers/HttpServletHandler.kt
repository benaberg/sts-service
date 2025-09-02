package fi.benaberg.sts.service.handlers

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import fi.benaberg.sts.service.LogRef
import fi.benaberg.sts.service.def.Constants
import fi.benaberg.sts.service.def.HttpResponse
import fi.benaberg.sts.service.util.JSONUtil
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Handles server initializing and request handling.
 */
class HttpServletHandler(
    private val log: LogRef,
    private val httpPort: Int,
    wsPort: Int,
    temperatureContext: String,
    dashboardContext: String,
    storageHandler: StorageHandler) {

    private val server: HttpServer by lazy { HttpServer.create(InetSocketAddress(httpPort), 0) }

    init {
        server.createContext(temperatureContext, TemperatureRequestHandler(log, storageHandler))
        server.createContext(dashboardContext, DashboardRequestHandler(log))
        server.createContext("/css/style.css", CSSHandler())
        server.createContext("/js/script.js", JSHandler(wsPort))
        server.createContext("/fonts/Cascadia.woff", FontHandler("Cascadia.woff"))
        server.createContext("/fonts/Consolas.woff", FontHandler("Consolas.woff"))
        server.createContext("/icons/favicon.ico", IconHandler("favicon.ico"))
        server.executor = null
    }

    fun start() {
        log.write("Setting up HTTP servlet on port: $httpPort")
        server.start()
    }

    private class TemperatureRequestHandler(private val log: LogRef, private val storageHandler: StorageHandler) : HttpHandler {

        override fun handle(exchange: HttpExchange) {
            when (exchange.requestMethod) {
                "GET" -> {
                    try {
                        val queryParams = getQueryParams(exchange)
                        if (queryParams.isEmpty()) {
                            // Serve current temperature
                            handleGetCurrentTemperature(exchange)
                        }
                        else {
                            // Serve temperature range
                            val from = queryParams[Constants.FROM]
                            val to = queryParams[Constants.TO]
                            if (from == null || to == null) {
                                handleGetCurrentTemperature(exchange)
                                return
                            }
                            handleGetTemperatureRange(exchange, from, to)
                        }
                    }
                    catch (exception: Exception) {
                        when (exception) {
                            is JSONException -> {
                                log.write("Could not compose temperature JSON. Reason: " + exception.message)
                            }
                            is IOException -> {
                                log.write("Could not write response. Reason: " + exception.message)
                            }
                        }
                        exchange.sendResponseHeaders(HttpResponse.INTERNAL_SERVER_ERROR, -1)
                    }
                }
                "PUT" -> {
                    try {
                        // Update current temperature
                        handlePutCurrentTemperature(exchange)
                    }
                    catch (exception: Exception) {
                        when (exception) {
                            is JSONException -> {
                                log.write("Error while storing reading: could not parse JSON in request body. Reason: ${exception.message}")
                            }
                            is IOException -> {
                                log.write("IOException while handling request. Reason: ${exception.message}")
                            }
                            else -> log.write("Error while handling request. Reason: ${exception.message}")
                        }
                        exchange.sendResponseHeaders(HttpResponse.INTERNAL_SERVER_ERROR, -1)
                    }
                }
            }
        }

        fun getQueryParams(exchange: HttpExchange): Map<String, Long> {
            val query = exchange.requestURI.rawQuery ?: return emptyMap()
            return try {
                query
                    .split("&")
                    .mapNotNull { param ->
                        val parts = param.split("=")
                        if (parts.size == 2) {
                            val key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8.name())
                            val value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name()).toLong()
                            key to value
                        } else null
                    }.toMap()
            }
            catch (exception: NumberFormatException) {
                emptyMap()
            }
        }

        private fun handleGetCurrentTemperature(exchange: HttpExchange) {
            log.write("Received GET current temperature from: ${exchange.remoteAddress} on path: ${exchange.requestURI}")

            // Extract and validate sensor ID
            val sensorId = getSensorId(exchange)
            if (sensorId == null) {
                exchange.sendResponseHeaders(HttpResponse.BAD_REQUEST, -1)
                return
            }

            // Compose reading JSON and send response headers
            val reading = storageHandler.getCurrentTemperatureReading(sensorId)
            if (reading != null) {
                val jsonString = JSONUtil.temperatureReadingToJSON(reading).toString()
                exchange.sendResponseHeaders(HttpResponse.OK, jsonString.length.toLong())

                // Write response
                val os = exchange.responseBody
                os.write(jsonString.toByteArray())
                os.close()
                log.write("Successfully served current temperature!")
            }
            else {
                exchange.sendResponseHeaders(HttpResponse.NOT_FOUND, -1)
                log.write("No temperature data found for sensor ID: $sensorId")
            }
        }

        private fun handleGetTemperatureRange(exchange: HttpExchange, from: Long, to: Long) {
            log.write("Received GET temperature range [$from - $to] from: ${exchange.remoteAddress} on path: ${exchange.requestURI}")

            // Extract and validate sensor ID
            val sensorId = getSensorId(exchange)
            if (sensorId == null) {
                exchange.sendResponseHeaders(HttpResponse.BAD_REQUEST, -1)
                return
            }

            // Get readings in range
            val jsonArray = JSONArray()
            val storedReadings = storageHandler.getStoredTemperatureReadings(sensorId)
            storedReadings.forEach { reading ->
                if (reading.timestamp in from..to) {
                    jsonArray.put(JSONUtil.temperatureReadingToJSON(reading))
                }
            }

            // Send response headers
            val jsonString = jsonArray.toString()
            exchange.sendResponseHeaders(HttpResponse.OK, jsonString.length.toLong())

            // Write response
            val os = exchange.responseBody
            os.write(jsonString.toByteArray())
            os.close()
            log.write("Successfully served ${jsonArray.length()} temperature readings in range!")
        }

        private fun handlePutCurrentTemperature(exchange: HttpExchange) {
            log.write("Received PUT temperature from ${exchange.remoteAddress}")

            // Extract and validate sensor ID
            val sensorId = getSensorId(exchange)
            if (sensorId == null) {
                exchange.sendResponseHeaders(HttpResponse.BAD_REQUEST, -1)
                return
            }

            // Read request
            val jsonString = String(exchange.requestBody.readAllBytes(), StandardCharsets.UTF_8)
            val jsonObject = JSONObject(jsonString)

            // Store temperature
            storageHandler.storeData(sensorId, jsonObject)

            // Send response headers
            exchange.sendResponseHeaders(HttpResponse.OK, -1)
            log.write("Successfully updated temperature!")
        }

        private fun getSensorId(exchange: HttpExchange) : Int? {
            return exchange.requestURI.path
                .removePrefix("/")
                .split("/")
                .getOrNull(1)
                ?.toIntOrNull()
        }
    }

    private class DashboardRequestHandler(private val log: LogRef) : HttpHandler {

        override fun handle(exchange: HttpExchange) {
            try {
                // Serve HTML
                val html = object {}.javaClass.getResource("/fi/benaberg/sts/service/html/dashboard.html")!!.readBytes()
                exchange.responseHeaders?.add("Content-Type", "text/html; charset=UTF-8")
                exchange.sendResponseHeaders(200, html.size.toLong())
                exchange.responseBody.use { it.write(html) }
            }
            catch (exception: Exception) {
                log.write("Failed to serve HTML: ${exception.message}")
            }
            finally {
                exchange.close()
            }
        }
    }

    private class CSSHandler : HttpHandler {

        override fun handle(exchange: HttpExchange) {
            // Serve CSS
            val css = object {}.javaClass.getResource("/fi/benaberg/sts/service/css/style.css")!!.readBytes()
            exchange.responseHeaders.add("Content-Type", "text/css")
            exchange.sendResponseHeaders(200, css.size.toLong())
            exchange.responseBody.use { it.write(css) }
        }
    }

    private class JSHandler(private val port: Int) : HttpHandler {

        override fun handle(exchange: HttpExchange) {
            // Get script and set Websocket port
            var js = object {}.javaClass.getResource("/fi/benaberg/sts/service/js/script.js")!!.readText()
            js = js.replace("{{WS_PORT}}", port.toString())

            // Serve JS
            val bytes = js.toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/javascript")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private class FontHandler(private val fontName: String) : HttpHandler {

        override fun handle(exchange: HttpExchange) {
            // Serve font
            val font = object {}.javaClass.getResource("/fi/benaberg/sts/service/fonts/$fontName")!!.readBytes()
            exchange.responseHeaders.add("Content-Type", "font/woff")
            exchange.sendResponseHeaders(200, font.size.toLong())
            exchange.responseBody.use { it.write(font) }
        }
    }

    private class IconHandler(private val iconName: String) : HttpHandler {

        override fun handle(exchange: HttpExchange) {
            // Serve icon
            val icon = object {}.javaClass.getResource("/fi/benaberg/sts/service/icons/$iconName")!!.readBytes()
            exchange.responseHeaders.add("Content-Type", "image/x-icon")
            exchange.sendResponseHeaders(200, icon.size.toLong())
            exchange.responseBody.use { it.write(icon) }
        }
    }
}