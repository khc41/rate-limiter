package study.latelimiter

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class LatelimiterApplication

fun main(args: Array<String>) {
    runApplication<LatelimiterApplication>(*args)
}
