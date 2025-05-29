package fi.benaberg.sts.service.handlers

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import fi.benaberg.sts.service.def.Constants
import fi.benaberg.sts.service.def.HttpResponse
import fi.benaberg.sts.service.LogRef
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
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
                    // Fetch temperature
                    try {
                        log.write("Received temperature GET")
                        // Compose response
                        val jsonObject = JSONObject()
                        jsonObject.put(Constants.TEMPERATURE, storageHandler.getTemperatureReading().temperature)
                        jsonObject.put(Constants.TIMESTAMP, storageHandler.getTemperatureReading().timestamp)

                        // Send response headers
                        val jsonString = jsonObject.toString()
                        exchange.sendResponseHeaders(HttpResponse.OK, jsonString.length.toLong())

                        // Write response
                        val os = exchange.responseBody
                        os.write(jsonString.toByteArray())
                        os.close()
                        log.write("Successfully served temperature!")
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
                    // Update temperature
                    try {
                        log.write("Received temperature PUT")

                        // Read request
                        val jsonString = String(exchange.requestBody.readAllBytes(), StandardCharsets.UTF_8)
                        val jsonObject = JSONObject(jsonString)

                        // Store temperature
                        storageHandler.storeData(jsonObject)

                        // Send response headers
                        exchange.sendResponseHeaders(HttpResponse.OK, -1)
                        log.write("Successfully updated temperature!")
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
            exchange.sendResponseHeaders(200, css.size.toLong())
            exchange.responseHeaders.add("Content-Type", "text/css")
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
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseHeaders.add("Content-Type", "application/javascript")
            exchange.responseBody.use { it.write(bytes) }
        }
    }
}