package study.ratelimiter

import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import study.ratelimiter.service.LoadTestService
import java.time.Duration

@Component
@Profile("stress-test")
class StressTestRunner(
    private val loadTestService: LoadTestService
) : CommandLineRunner {

    private val baseUrl = "http://localhost:8080"

    override fun run(vararg args: String?) {
        println("=== Rate Limiter 부하 테스트 시작 ===")
        
        // 테스트 시나리오 선택
        val scenario = args.firstOrNull() ?: "all"
        
        when (scenario) {
            "token-bucket" -> runTokenBucketTests()
            "fixed-window" -> runFixedWindowTests()
            "sliding-window" -> runSlidingWindowTests()
            "comparison" -> runComparisonTests()
            "all" -> runAllTests()
            else -> {
                println("사용법: --spring.profiles.active=stress-test [시나리오]")
                println("시나리오: token-bucket, fixed-window, sliding-window, comparison, all")
            }
        }
        
        println("=== 부하 테스트 완료 ===")
        System.exit(0)
    }

    private fun runTokenBucketTests() {
        println("\n=== 토큰 버킷 부하 테스트 ===")
        
        // 버스트 테스트
        val burstResults = loadTestService.runBurstRequests(baseUrl, "/api/test/token-bucket", 10, 50)
            .collectList()
            .block(Duration.ofSeconds(30)) ?: emptyList()
        
        val burstAnalysis = loadTestService.analyzeResults(burstResults)
        loadTestService.printAnalysis(burstAnalysis, "토큰 버킷 - 버스트 테스트")
        
        // 지속적 부하 테스트
        Thread.sleep(5000)
        val sustainedResults = loadTestService.runSustainedLoad(baseUrl, "/api/test/token-bucket", 15L, 1)
            .collectList()
            .block(Duration.ofSeconds(20)) ?: emptyList()
        
        val sustainedAnalysis = loadTestService.analyzeResults(sustainedResults)
        loadTestService.printAnalysis(sustainedAnalysis, "토큰 버킷 - 지속적 부하 테스트")
    }

    private fun runFixedWindowTests() {
        println("\n=== 고정 윈도우 부하 테스트 ===")
        
        // 윈도우 경계 테스트
        val firstWindow = loadTestService.runBurstRequests(baseUrl, "/api/test/fixed-window", 5, 100)
            .collectList()
            .block(Duration.ofSeconds(10)) ?: emptyList()
        
        val firstAnalysis = loadTestService.analyzeResults(firstWindow)
        loadTestService.printAnalysis(firstAnalysis, "고정 윈도우 - 첫 번째 윈도우")
        
        // 윈도우 경계 대기
        println("20초 대기하여 윈도우 경계를 넘어가도록...")
        Thread.sleep(20000)
        
        val secondWindow = loadTestService.runBurstRequests(baseUrl, "/api/test/fixed-window", 5, 100)
            .collectList()
            .block(Duration.ofSeconds(10)) ?: emptyList()
        
        val secondAnalysis = loadTestService.analyzeResults(secondWindow)
        loadTestService.printAnalysis(secondAnalysis, "고정 윈도우 - 두 번째 윈도우")
    }

    private fun runSlidingWindowTests() {
        println("\n=== 슬라이딩 윈도우 부하 테스트 ===")
        
        // 점진적 부하 테스트
        val phases = listOf(
            Triple(3, 1, "낮은 부하"),
            Triple(6, 2, "중간 부하"),
            Triple(8, 3, "높은 부하")
        )
        
        val allResults = mutableListOf<study.ratelimiter.service.TestResult>()
        
        phases.forEach { (requestCount, concurrency, phaseName) ->
            println("=== $phaseName 단계 시작 ===")
            
            val results = loadTestService.runConcurrentRequests(baseUrl, "/api/test/sliding-window", requestCount, concurrency)
                .collectList()
                .block(Duration.ofSeconds(20)) ?: emptyList()
            
            val analysis = loadTestService.analyzeResults(results)
            loadTestService.printAnalysis(analysis, "슬라이딩 윈도우 - $phaseName")
            
            allResults.addAll(results)
            
            if (phaseName != "높은 부하") {
                println("다음 단계로 진행하기 위해 3초 대기...")
                Thread.sleep(3000)
            }
        }
        
        val totalAnalysis = loadTestService.analyzeResults(allResults)
        loadTestService.printAnalysis(totalAnalysis, "슬라이딩 윈도우 - 전체 점진적 부하 테스트")
    }

    private fun runComparisonTests() {
        println("\n=== 알고리즘 비교 테스트 ===")
        
        val endpoints = mapOf(
            "토큰 버킷" to "/api/test/token-bucket",
            "고정 윈도우" to "/api/test/fixed-window",
            "슬라이딩 윈도우" to "/api/test/sliding-window"
        )
        
        val results = mutableMapOf<String, study.ratelimiter.service.LoadTestAnalysis>()
        
        endpoints.forEach { (name, endpoint) ->
            println("\n=== $name 테스트 시작 ===")
            
            val testResults = loadTestService.runConcurrentRequests(baseUrl, endpoint, 8, 2)
                .collectList()
                .block(Duration.ofSeconds(30)) ?: emptyList()
            
            val analysis = loadTestService.analyzeResults(testResults)
            results[name] = analysis
            loadTestService.printAnalysis(analysis, name)
            
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

    private fun runAllTests() {
        runTokenBucketTests()
        Thread.sleep(3000)
        runFixedWindowTests()
        Thread.sleep(3000)
        runSlidingWindowTests()
        Thread.sleep(3000)
        runComparisonTests()
    }
} 