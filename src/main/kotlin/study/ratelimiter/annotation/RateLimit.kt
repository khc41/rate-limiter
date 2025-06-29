package study.ratelimiter.annotation

import study.ratelimiter.ratelimiter.RateLimiterType

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RateLimit(
    val type: RateLimiterType = RateLimiterType.TOKEN_BUCKET,
    val limit: Int = 10,
    val windowSeconds: Long = 60L,
    val key: String = ""
) 