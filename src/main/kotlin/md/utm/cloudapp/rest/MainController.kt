package md.utm.cloudapp.rest

import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class MainController {

    private val logger = LoggerFactory.getLogger(MainController::class.java)

    @GetMapping("/")
    fun main(): String {
        logger.info("Received request on /")
        val response = "Hello World in the deploy for the presentation!!!"
        logger.debug("Response: $response")
        return response
    }

    @GetMapping("/simulate-error")
    fun error(): String {
        logger.warn("Simulated warning triggered")
        logger.error("Simulated error occurred", RuntimeException("This is a test exception"))
        return "Something went wrong!"
    }
}
