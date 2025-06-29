package study.ratelimiter.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import study.ratelimiter.aspect.RateLimitExceededException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimitExceeded(ex: RateLimitExceededException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            error = "Rate Limit Exceeded",
            message = ex.message ?: "Too many requests",
            status = HttpStatus.TOO_MANY_REQUESTS.value()
        )
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(errorResponse)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            error = "Internal Server Error",
            message = ex.message ?: "An unexpected error occurred",
            status = HttpStatus.INTERNAL_SERVER_ERROR.value()
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }
}

data class ErrorResponse(
    val error: String,
    val message: String,
    val status: Int
) 