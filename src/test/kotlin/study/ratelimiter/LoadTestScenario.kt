package study.ratelimiter

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.TestPropertySource
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import study.ratelimiter.service.LoadTestAnalysis
import study.ratelimiter.service.TestResult
import java.time.Duration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
    ]
)
abstract class LoadTestScenario {

    @LocalServerPort
    protected var port: Int = 0

    protected val webClient: WebClient = WebClient.builder()
        .filter(loggingFilter())
        .build()

    protected val baseUrl: String
        get() = "http://localhost:$port"

    private fun loggingFilter(): ExchangeFilterFunction {
        return ExchangeFilterFunction.ofRequestProcessor { request ->
            // 로그 레벨을 줄이기 위해 주석 처리
            // println("Request: ${request.method()} ${request.url()}")
            Mono.just(request)
        }
    }

    protected fun makeRequest(endpoint: String): Mono<TestResult> {
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

    protected fun runConcurrentRequests(
        endpoint: String,
        requestCount: Int,
        concurrency: Int = 10
    ): Flux<TestResult> {
        return Flux.range(1, requestCount)
            .flatMap({ makeRequest(endpoint) }, concurrency)
    }

    protected fun runBurstRequests(
        endpoint: String,
        burstSize: Int,
        delayMs: Long = 100
    ): Flux<TestResult> {
        return Flux.range(1, burstSize)
            .flatMap({ makeRequest(endpoint) }, burstSize)
            .delayElements(Duration.ofMillis(delayMs))
    }

    protected fun runSustainedLoad(
        endpoint: String,
        durationSeconds: Long,
        requestsPerSecond: Int
    ): Flux<TestResult> {
        val totalRequests = (durationSeconds * requestsPerSecond).toInt()
        val intervalMs = 1000L / requestsPerSecond

        return Flux.range(1, totalRequests)
            .flatMap({ makeRequest(endpoint) }, requestsPerSecond)
            .delayElements(Duration.ofMillis(intervalMs))
    }

    protected fun analyzeResults(results: List<TestResult>): LoadTestAnalysis {
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

    protected fun printAnalysis(analysis: LoadTestAnalysis, scenarioName: String) {
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