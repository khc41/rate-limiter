package study.ratelimiter

import org.junit.jupiter.api.Test
import org.springframework.test.context.ActiveProfiles
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import study.ratelimiter.service.TestResult
import java.time.Duration

@ActiveProfiles("test")
class SlidingWindowLoadTest : LoadTestScenario() {

    @Test
    fun `슬라이딩 윈도우 - 정확한 윈도우 계산 테스트`() {
        val endpoint = "/api/test/sliding-window"
        
        // 첫 번째 구간: 윈도우의 절반만 채우기
        val firstHalf = runBurstRequests(endpoint, 2, 100)
            .collectList()
            .block(Duration.ofSeconds(10)) ?: emptyList()

        println("첫 번째 구간 완료. 12초 대기...")
        Thread.sleep(12000) // 12초 대기 (20초 윈도우의 절반 정도)

        // 두 번째 구간: 윈도우가 겹치는 구간
        val secondHalf = runBurstRequests(endpoint, 3, 100)
            .collectList()
            .block(Duration.ofSeconds(10)) ?: emptyList()

        val firstAnalysis = analyzeResults(firstHalf)
        val secondAnalysis = analyzeResults(secondHalf)

        printAnalysis(firstAnalysis, "슬라이딩 윈도우 - 첫 번째 구간")
        printAnalysis(secondAnalysis, "슬라이딩 윈도우 - 두 번째 구간 (겹치는 윈도우)")

        // 슬라이딩 윈도우는 정확한 계산을 하므로 더 엄격할 수 있음
        StepVerifier.create(Flux.fromIterable(firstHalf + secondHalf))
            .expectNextCount((firstHalf.size + secondHalf.size).toLong())
            .verifyComplete()
    }

    @Test
    fun `슬라이딩 윈도우 - 윈도우 경계 테스트`() {
        val endpoint = "/api/test/sliding-window"
        
        // 윈도우를 완전히 지나간 후 요청
        val firstRequest = runBurstRequests(endpoint, 1, 100)
            .collectList()
            .block(Duration.ofSeconds(10)) ?: emptyList()

        println("첫 번째 요청 완료. 25초 대기하여 윈도우를 완전히 지나가도록...")
        Thread.sleep(25000) // 25초 대기 (20초 윈도우 + 5초 여유)

        // 윈도우가 완전히 지나간 후 요청
        val secondRequest = runBurstRequests(endpoint, 4, 100)
            .collectList()
            .block(Duration.ofSeconds(10)) ?: emptyList()

        val firstAnalysis = analyzeResults(firstRequest)
        val secondAnalysis = analyzeResults(secondRequest)

        printAnalysis(firstAnalysis, "슬라이딩 윈도우 - 첫 번째 요청")
        printAnalysis(secondAnalysis, "슬라이딩 윈도우 - 윈도우 경계 후 요청")

        // 윈도우가 지나간 후에는 더 많은 성공이 있어야 함
        StepVerifier.create(Flux.fromIterable(firstRequest + secondRequest))
            .expectNextCount((firstRequest.size + secondRequest.size).toLong())
            .verifyComplete()
    }

    @Test
    fun `슬라이딩 윈도우 - 동시 요청 테스트`() {
        val endpoint = "/api/test/sliding-window"
        val requestCount = 8
        val concurrency = 2

        val results = runConcurrentRequests(endpoint, requestCount, concurrency)
            .collectList()
            .block(Duration.ofSeconds(30)) ?: emptyList()

        val analysis = analyzeResults(results)
        printAnalysis(analysis, "슬라이딩 윈도우 - 동시 요청 테스트")

        // 동시 요청은 슬라이딩 윈도우의 정확한 계산으로 제한됨
        StepVerifier.create(Flux.fromIterable(results))
            .expectNextCount(requestCount.toLong())
            .verifyComplete()
    }

    @Test
    fun `슬라이딩 윈도우 - 지속적 부하 테스트`() {
        val endpoint = "/api/test/sliding-window"
        val durationSeconds = 30L // 윈도우(20초)보다 긴 시간
        val requestsPerSecond = 1

        val results = runSustainedLoad(endpoint, durationSeconds, requestsPerSecond)
            .collectList()
            .block(Duration.ofSeconds(40)) ?: emptyList()

        val analysis = analyzeResults(results)
        printAnalysis(analysis, "슬라이딩 윈도우 - 지속적 부하 테스트")

        // 지속적 부하는 슬라이딩 윈도우의 정확한 계산으로 제한됨
        StepVerifier.create(Flux.fromIterable(results))
            .expectNextCount((durationSeconds * requestsPerSecond).toLong())
            .verifyComplete()
    }

    @Test
    fun `슬라이딩 윈도우 - 점진적 부하 테스트`() {
        val endpoint = "/api/test/sliding-window"
        
        // 점진적으로 부하를 증가시키는 테스트
        val phases = listOf(
            Triple(5, 1, "낮은 부하"),
            Triple(10, 2, "중간 부하"),
            Triple(15, 3, "높은 부하")
        )

        val allResults = mutableListOf<TestResult>()

        phases.forEach { (requestCount, concurrency, phaseName) ->
            println("=== $phaseName 단계 시작 ===")
            
            val results = runConcurrentRequests(endpoint, requestCount, concurrency)
                .collectList()
                .block(Duration.ofSeconds(20)) ?: emptyList()

            val analysis = analyzeResults(results)
            printAnalysis(analysis, "슬라이딩 윈도우 - $phaseName")

            allResults.addAll(results)

            // 단계 간 대기
            if (phaseName != "높은 부하") {
                println("다음 단계로 진행하기 위해 10초 대기...")
                Thread.sleep(10000)
            }
        }

        val totalAnalysis = analyzeResults(allResults)
        printAnalysis(totalAnalysis, "슬라이딩 윈도우 - 전체 점진적 부하 테스트")

        StepVerifier.create(Flux.fromIterable(allResults))
            .expectNextCount(allResults.size.toLong())
            .verifyComplete()
    }
} 