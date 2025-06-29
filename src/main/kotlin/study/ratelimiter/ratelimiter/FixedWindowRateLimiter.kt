package study.ratelimiter.ratelimiter

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class FixedWindowRateLimiter(
    private val redisTemplate: RedisTemplate<String, String>
) : RateLimiter {

    companion object {
        private const val DEFAULT_LIMIT = 10
        private const val DEFAULT_WINDOW_SECONDS = 60L
    }

    override fun isAllowed(key: String): Boolean {
        return isAllowed(key, DEFAULT_LIMIT, DEFAULT_WINDOW_SECONDS)
    }

    override fun isAllowed(key: String, limit: Int, windowSeconds: Long): Boolean {
        val windowKey = "fixed_window:$key"
        val now = Instant.now().epochSecond
        val windowStart = (now / windowSeconds) * windowSeconds
        
        val script = """
            local window_key = KEYS[1]
            local window_start = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local window_seconds = tonumber(ARGV[3])
            
            -- Get current count for this window
            local current_count = tonumber(redis.call('GET', window_key)) or 0
            
            -- Check if we can increment
            if current_count < limit then
                redis.call('INCR', window_key)
                redis.call('EXPIRE', window_key, window_seconds)
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
                    windowKey.toByteArray(),
                    windowStart.toString().toByteArray(),
                    limit.toString().toByteArray(),
                    windowSeconds.toString().toByteArray()
                )
            }
        ) ?: 0L

        return result == 1L
    }
} 