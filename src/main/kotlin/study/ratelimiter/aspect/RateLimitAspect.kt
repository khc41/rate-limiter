package study.ratelimiter.aspect

import jakarta.servlet.http.HttpServletRequest
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import study.ratelimiter.annotation.RateLimit
import study.ratelimiter.ratelimiter.RateLimiterFactory

@Aspect
@Component
class RateLimitAspect(
    private val rateLimiterFactory: RateLimiterFactory
) {

    @Around("@annotation(rateLimit)")
    fun rateLimit(joinPoint: ProceedingJoinPoint, rateLimit: RateLimit): Any? {
        val key = generateKey(rateLimit, joinPoint)
        val rateLimiter = rateLimiterFactory.getRateLimiter(rateLimit.type)
        
        val isAllowed = if (rateLimit.key.isNotEmpty()) {
            rateLimiter.isAllowed(rateLimit.key, rateLimit.limit, rateLimit.windowSeconds)
        } else {
            rateLimiter.isAllowed(key, rateLimit.limit, rateLimit.windowSeconds)
        }
        
        if (!isAllowed) {
            throw RateLimitExceededException("Rate limit exceeded for key: $key")
        }
        
        return joinPoint.proceed()
    }
    
    private fun generateKey(rateLimit: RateLimit, joinPoint: ProceedingJoinPoint): String {
        val request = (RequestContextHolder.currentRequestAttributes() as? ServletRequestAttributes)?.request
        
        return when {
            rateLimit.key.isNotEmpty() -> rateLimit.key
            request != null -> {
                val clientIp = getClientIp(request)
                val method = request.method
                val path = request.requestURI
                "$clientIp:$method:$path"
            }
            else -> {
                val className = joinPoint.signature.declaringType.simpleName
                val methodName = joinPoint.signature.name
                "$className:$methodName"
            }
        }
    }
    
    private fun getClientIp(request: HttpServletRequest): String {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        return when {
            xForwardedFor != null && xForwardedFor.isNotEmpty() -> xForwardedFor.split(",")[0].trim()
            else -> request.remoteAddr
        }
    }
}

class RateLimitExceededException(message: String) : RuntimeException(message) 