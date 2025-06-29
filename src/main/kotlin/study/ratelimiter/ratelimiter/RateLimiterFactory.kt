package study.ratelimiter.ratelimiter

import org.springframework.stereotype.Component

@Component
class RateLimiterFactory(
    private val tokenBucketRateLimiter: TokenBucketRateLimiter,
    private val fixedWindowRateLimiter: FixedWindowRateLimiter,
    private val slidingWindowRateLimiter: SlidingWindowRateLimiter
) {
    
    fun getRateLimiter(type: RateLimiterType): RateLimiter {
        return when (type) {
            RateLimiterType.TOKEN_BUCKET -> tokenBucketRateLimiter
            RateLimiterType.FIXED_WINDOW -> fixedWindowRateLimiter
            RateLimiterType.SLIDING_WINDOW -> slidingWindowRateLimiter
        }
    }
} 