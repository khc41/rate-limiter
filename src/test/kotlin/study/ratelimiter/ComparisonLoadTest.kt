package study.ratelimiter

import org.junit.jupiter.api.Test
import org.springframework.test.context.ActiveProfiles
import study.ratelimiter.service.LoadTestAnalysis
import study.ratelimiter.service.TestResult
import java.time.Duration

@ActiveProfiles("test")
class ComparisonLoadTest : LoadTestScenario() {

    @Test
    fun `알고리즘 비교 - 동일한 부하 조건에서의 성능 비교`() {
        val endpoints = mapOf(
            "토큰 버킷" to "/api/test/token-bucket",
            "고정 윈도우" to "/api/test/fixed-window",
            "슬라이딩 윈도우" to "/api/test/sliding-window"
        )

        val requestCount = 10
        val concurrency = 3

        val results = mutableMapOf<String, LoadTestAnalysis>()

        endpoints.forEach { (name, endpoint) ->
            println("\n=== $name 테스트 시작 ===")
            
            val testResults = runConcurrentRequests(endpoint, requestCount, concurrency)
                .collectList()
                .block(Duration.ofSeconds(30)) ?: emptyList()

            val analysis = analyzeResults(testResults)
            results[name] = analysis
            printAnalysis(analysis, name)

            // 다음 알고리즘 테스트 전 대기
            if (name != "슬라이딩 윈도우") {
                println("다음 알고리즘 테스트를 위해 3초 대기...")
                Thread.sleep(3000)
            }
        }

        // 결과 비교
        println("\n=== 알고리즘 성능 비교 ===")
        results.forEach { (name, analysis) ->
            println("$name:")
            println("  - 성공률: ${String.format("%.2f%%", analysis.successRate * 100)}")
            println("  - Rate Limit 비율: ${String.format("%.2f%%", analysis.rateLimitRate * 100)}")
            println("  - 평균 응답 시간: ${String.format("%.2fms", analysis.averageResponseTime)}")
        }
        println("========================\n")
    }

    @Test
    fun `알고리즘 비교 - 버스트 트래픽 처리 능력`() {
        val endpoints = mapOf(
            "토큰 버킷" to "/api/test/token-bucket",
            "고정 윈도우" to "/api/test/fixed-window",
            "슬라이딩 윈도우" to "/api/test/sliding-window"
        )

        val burstSize = 8

        val results = mutableMapOf<String, LoadTestAnalysis>()

        endpoints.forEach { (name, endpoint) ->
            println("\n=== $name 버스트 테스트 시작 ===")
            
            val testResults = runBurstRequests(endpoint, burstSize, 50)
                .collectList()
                .block(Duration.ofSeconds(20)) ?: emptyList()

            val analysis = analyzeResults(testResults)
            results[name] = analysis
            printAnalysis(analysis, name)

            // 다음 알고리즘 테스트 전 대기
            if (name != "슬라이딩 윈도우") {
                println("다음 알고리즘 테스트를 위해 3초 대기...")
                Thread.sleep(3000)
            }
        }

        // 버스트 처리 능력 비교
        println("\n=== 버스트 트래픽 처리 능력 비교 ===")
        results.forEach { (name, analysis) ->
            println("$name:")
            println("  - 버스트 허용률: ${String.format("%.2f%%", analysis.successRate * 100)}")
            println("  - 버스트 차단률: ${String.format("%.2f%%", analysis.rateLimitRate * 100)}")
        }
        println("========================\n")
    }

    @Test
    fun `알고리즘 비교 - 지속적 부하 처리 능력`() {
        val endpoints = mapOf(
            "토큰 버킷" to "/api/test/token-bucket",
            "고정 윈도우" to "/api/test/fixed-window",
            "슬라이딩 윈도우" to "/api/test/sliding-window"
        )

        val durationSeconds = 15L
        val requestsPerSecond = 1

        val results = mutableMapOf<String, LoadTestAnalysis>()

        endpoints.forEach { (name, endpoint) ->
            println("\n=== $name 지속적 부하 테스트 시작 ===")
            
            val testResults = runSustainedLoad(endpoint, durationSeconds, requestsPerSecond)
                .collectList()
                .block(Duration.ofSeconds(20)) ?: emptyList()

            val analysis = analyzeResults(testResults)
            results[name] = analysis
            printAnalysis(analysis, name)

            // 다음 알고리즘 테스트 전 대기
            if (name != "슬라이딩 윈도우") {
                println("다음 알고리즘 테스트를 위해 3초 대기...")
                Thread.sleep(3000)
            }
        }

        // 지속적 부하 처리 능력 비교
        println("\n=== 지속적 부하 처리 능력 비교 ===")
        results.forEach { (name, analysis) ->
            println("$name:")
            println("  - 지속적 처리 성공률: ${String.format("%.2f%%", analysis.successRate * 100)}")
            println("  - 평균 처리량: ${String.format("%.2f req/s", analysis.successfulRequests.toDouble() / durationSeconds)}")
        }
        println("========================\n")
    }

    @Test
    fun `알고리즘 비교 - 복합 시나리오 테스트`() {
        val endpoints = mapOf(
            "토큰 버킷" to "/api/test/token-bucket",
            "고정 윈도우" to "/api/test/fixed-window",
            "슬라이딩 윈도우" to "/api/test/sliding-window"
        )

        val results = mutableMapOf<String, MutableList<TestResult>>()

        endpoints.forEach { (name, endpoint) ->
            println("\n=== $name 복합 시나리오 테스트 시작 ===")
            
            val allResults = mutableListOf<TestResult>()

            // 1단계: 초기 버스트
            println("1단계: 초기 버스트 (5개 요청)")
            val burstResults = runBurstRequests(endpoint, 5, 100)
                .collectList()
                .block(Duration.ofSeconds(10)) ?: emptyList()
            allResults.addAll(burstResults)

            // 2단계: 대기 후 지속적 부하
            println("2단계: 10초 대기 후 지속적 부하 (10초간 초당 1개)")
            Thread.sleep(10000)
            val sustainedResults = runSustainedLoad(endpoint, 10L, 1)
                .collectList()
                .block(Duration.ofSeconds(15)) ?: emptyList()
            allResults.addAll(sustainedResults)

            // 3단계: 최종 버스트
            println("3단계: 최종 버스트 (3개 요청)")
            val finalBurstResults = runBurstRequests(endpoint, 3, 100)
                .collectList()
                .block(Duration.ofSeconds(10)) ?: emptyList()
            allResults.addAll(finalBurstResults)

            results[name] = allResults

            val analysis = analyzeResults(allResults)
            printAnalysis(analysis, "$name - 복합 시나리오")

            // 다음 알고리즘 테스트 전 대기
            if (name != "슬라이딩 윈도우") {
                println("다음 알고리즘 테스트를 위해 3초 대기...")
                Thread.sleep(3000)
            }
        }

        // 복합 시나리오 결과 비교
        println("\n=== 복합 시나리오 성능 비교 ===")
        results.forEach { (name, testResults) ->
            val analysis = analyzeResults(testResults)
            println("$name:")
            println("  - 전체 성공률: ${String.format("%.2f%%", analysis.successRate * 100)}")
            println("  - 전체 Rate Limit 비율: ${String.format("%.2f%%", analysis.rateLimitRate * 100)}")
            println("  - 총 처리된 요청: ${analysis.totalRequests}")
        }
        println("========================\n")
    }
} 