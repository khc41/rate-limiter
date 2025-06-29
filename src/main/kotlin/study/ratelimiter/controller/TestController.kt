package study.ratelimiter.controller

import org.springframework.web.bind.annotation.*
import study.ratelimiter.annotation.RateLimit
import study.ratelimiter.ratelimiter.RateLimiterType
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/test")
class TestController {

    @GetMapping("/token-bucket")
    @RateLimit(type = RateLimiterType.TOKEN_BUCKET, limit = 5, windowSeconds = 30)
    fun testTokenBucket(): Map<String, Any> {
        return mapOf(
            "message" to "Token Bucket Rate Limiter Test",
            "timestamp" to LocalDateTime.now(),
            "algorithm" to "Token Bucket"
        )
    }

    @GetMapping("/fixed-window")
    @RateLimit(type = RateLimiterType.FIXED_WINDOW, limit = 3, windowSeconds = 15)
    fun testFixedWindow(): Map<String, Any> {
        return mapOf(
            "message" to "Fixed Window Rate Limiter Test",
            "timestamp" to LocalDateTime.now(),
            "algorithm" to "Fixed Window Counter"
        )
    }

    @GetMapping("/sliding-window")
    @RateLimit(type = RateLimiterType.SLIDING_WINDOW, limit = 4, windowSeconds = 20)
    fun testSlidingWindow(): Map<String, Any> {
        return mapOf(
            "message" to "Sliding Window Rate Limiter Test",
            "timestamp" to LocalDateTime.now(),
            "algorithm" to "Sliding Window Log"
        )
    }

    @GetMapping("/custom-key")
    @RateLimit(type = RateLimiterType.TOKEN_BUCKET, limit = 2, windowSeconds = 30, key = "custom-test-key")
    fun testCustomKey(): Map<String, Any> {
        return mapOf(
            "message" to "Custom Key Rate Limiter Test",
            "timestamp" to LocalDateTime.now(),
            "algorithm" to "Token Bucket with Custom Key"
        )
    }

    @GetMapping("/health")
    fun health(): Map<String, Any> {
        return mapOf(
            "status" to "OK",
            "timestamp" to LocalDateTime.now(),
            "service" to "Rate Limiter Service"
        )
    }
} 