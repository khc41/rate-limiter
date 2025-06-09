package study.ratelimiter

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class RatelimiterApplication

fun main(args: Array<String>) {
    runApplication<RatelimiterApplication>(*args)
}
