package study.ratelimiter.service

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

@Service
class LoadTestService {

    private val webClient: WebClient = WebClient.builder()
        .filter(loggingFilter())
        .build()

    private fun loggingFilter(): ExchangeFilterFunction {
        return ExchangeFilterFunction.ofRequestProcessor { request ->
            // 로그 레벨을 줄이기 위해 주석 처리
            // println("Request: ${request.method()} ${request.url()}")
            Mono.just(request)
        }
    }

    fun makeRequest(baseUrl: String, endpoint: String): Mono<TestResult> {
        val startTime = System.currentTimeMillis()
        
        return webClient.get()
            .uri("$baseUrl$endpoint")
            .retrieve()
            .toBodilessEntity()
            .map { response ->
                val endTime = System.currentTimeMillis()
                TestResult(
                    statusCode = response.statusCode.value(),
                    success = response.statusCode.is2xxSuccessful,
                    responseTime = endTime - startTime,
                    timestamp = endTime
                )
            }
            .onErrorResume { error ->
                val endTime = System.currentTimeMillis()
                Mono.just(
                    TestResult(
                        statusCode = if (error.message?.contains("429") == true) 429 else 500,
                        success = false,
                        responseTime = endTime - startTime,
                        timestamp = endTime,
                        error = error.message
                    )
                )
            }
    }

    fun runConcurrentRequests(
        baseUrl: String,
        endpoint: String,
        requestCount: Int,
        concurrency: Int = 10
    ): Flux<TestResult> {
        return Flux.range(1, requestCount)
            .flatMap({ makeRequest(baseUrl, endpoint) }, concurrency)
    }

    fun runBurstRequests(
        baseUrl: String,
        endpoint: String,
        burstSize: Int,
        delayMs: Long = 100
    ): Flux<TestResult> {
        return Flux.range(1, burstSize)
            .flatMap({ makeRequest(baseUrl, endpoint) }, burstSize)
            .delayElements(Duration.ofMillis(delayMs))
    }

    fun runSustainedLoad(
        baseUrl: String,
        endpoint: String,
        durationSeconds: Long,
        requestsPerSecond: Int
    ): Flux<TestResult> {
        val totalRequests = (durationSeconds * requestsPerSecond).toInt()
        val intervalMs = 1000L / requestsPerSecond

        return Flux.range(1, totalRequests)
            .flatMap({ makeRequest(baseUrl, endpoint) }, requestsPerSecond)
            .delayElements(Duration.ofMillis(intervalMs))
    }

    fun analyzeResults(results: List<TestResult>): LoadTestAnalysis {
        val totalRequests = results.size
        val successfulRequests = results.count { it.success }
        val failedRequests = totalRequests - successfulRequests
        val rateLimitedRequests = results.count { it.statusCode == 429 }

        val responseTimes = results.map { it.responseTime }
        val avgResponseTime = if (responseTimes.isNotEmpty()) {
            responseTimes.average()
        } else 0.0

        return LoadTestAnalysis(
            totalRequests = totalRequests,
            successfulRequests = successfulRequests,
            failedRequests = failedRequests,
            rateLimitedRequests = rateLimitedRequests,
            successRate = successfulRequests.toDouble() / totalRequests,
            rateLimitRate = rateLimitedRequests.toDouble() / totalRequests,
            averageResponseTime = avgResponseTime
        )
    }

    fun printAnalysis(analysis: LoadTestAnalysis, scenarioName: String) {
        println("\n=== $scenarioName ===")
        println("총 요청 수: ${analysis.totalRequests}")
        println("성공 요청 수: ${analysis.successfulRequests}")
        println("실패 요청 수: ${analysis.failedRequests}")
        println("Rate Limited 요청 수: ${analysis.rateLimitedRequests}")
        println("성공률: ${String.format("%.2f%%", analysis.successRate * 100)}")
        println("Rate Limit 비율: ${String.format("%.2f%%", analysis.rateLimitRate * 100)}")
        println("평균 응답 시간: ${String.format("%.2fms", analysis.averageResponseTime)}")
        println("========================\n")
    }
}

data class TestResult(
    val statusCode: Int,
    val success: Boolean,
    val responseTime: Long, // 응답 시간 (ms)
    val timestamp: Long,    // 요청 완료 시간
    val error: String? = null
)

data class LoadTestAnalysis(
    val totalRequests: Int,
    val successfulRequests: Int,
    val failedRequests: Int,
    val rateLimitedRequests: Int,
    val successRate: Double,
    val rateLimitRate: Double,
    val averageResponseTime: Double
) 