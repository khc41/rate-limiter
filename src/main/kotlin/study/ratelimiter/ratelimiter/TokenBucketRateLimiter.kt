package study.ratelimiter.ratelimiter

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class TokenBucketRateLimiter(
    private val redisTemplate: RedisTemplate<String, String>
) : RateLimiter {

    companion object {
        private const val DEFAULT_CAPACITY = 10
        private const val DEFAULT_REFILL_RATE = 1.0 // tokens per second
        private const val DEFAULT_WINDOW_SECONDS = 60L
    }

    override fun isAllowed(key: String): Boolean {
        return isAllowed(key, DEFAULT_CAPACITY, DEFAULT_WINDOW_SECONDS)
    }

    override fun isAllowed(key: String, limit: Int, windowSeconds: Long): Boolean {
        val bucketKey = "token_bucket:$key"
        val now = Instant.now().epochSecond
        
        val script = """
            local bucket_key = KEYS[1]
            local now = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local refill_rate = tonumber(ARGV[3])
            
            local bucket_data = redis.call('HMGET', bucket_key, 'tokens', 'last_refill')
            local current_tokens = tonumber(bucket_data[1]) or capacity
            local last_refill = tonumber(bucket_data[2]) or now
            
            -- Calculate time passed and refill tokens
            local time_passed = now - last_refill
            local tokens_to_add = time_passed * refill_rate
            current_tokens = math.min(capacity, current_tokens + tokens_to_add)
            
            -- Check if we can consume a token
            if current_tokens >= 1 then
                current_tokens = current_tokens - 1
                redis.call('HMSET', bucket_key, 'tokens', current_tokens, 'last_refill', now)
                redis.call('EXPIRE', bucket_key, 3600) -- Expire after 1 hour
                return 1
            else
                return 0
            end
        """.trimIndent()

        val result = redisTemplate.execute(
            { connection ->
                connection.eval(
                    script.toByteArray(),
                    org.springframework.data.redis.connection.ReturnType.INTEGER,
                    1,
                    bucketKey.toByteArray(),
                    now.toString().toByteArray(),
                    limit.toString().toByteArray(),
                    DEFAULT_REFILL_RATE.toString().toByteArray()
                )
            }
        ) ?: 0L

        return result == 1L
    }
} 