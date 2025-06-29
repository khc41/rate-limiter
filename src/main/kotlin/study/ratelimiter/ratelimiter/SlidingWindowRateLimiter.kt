package study.ratelimiter.ratelimiter

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class SlidingWindowRateLimiter(
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
        val logKey = "sliding_window:$key"
        val now = Instant.now().epochSecond
        val windowStart = now - windowSeconds
        
        val script = """
            local log_key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window_start = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local window_seconds = tonumber(ARGV[4])
            
            -- Remove old entries outside the window
            redis.call('ZREMRANGEBYSCORE', log_key, 0, window_start)
            
            -- Count current entries in the window
            local current_count = redis.call('ZCARD', log_key)
            
            -- Check if we can add a new entry
            if current_count < limit then
                redis.call('ZADD', log_key, now, now .. ':' .. math.random())
                redis.call('EXPIRE', log_key, window_seconds * 2) -- Expire after 2x window
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
                    logKey.toByteArray(),
                    now.toString().toByteArray(),
                    windowStart.toString().toByteArray(),
                    limit.toString().toByteArray(),
                    windowSeconds.toString().toByteArray()
                )
            }
        ) ?: 0L

        return result == 1L
    }
} 