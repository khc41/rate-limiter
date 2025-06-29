package study.ratelimiter.ratelimiter

interface RateLimiter {
    fun isAllowed(key: String): Boolean
    fun isAllowed(key: String, limit: Int, windowSeconds: Long): Boolean
} 