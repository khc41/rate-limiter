package study.ratelimiter

import org.junit.jupiter.api.Test
import org.springframework.test.context.ActiveProfiles
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Duration

@ActiveProfiles("test")
class FixedWindowLoadTest : LoadTestScenario() {

    @Test
    fun `고정 윈도우 - 윈도우 경계 테스트`() {
        val endpoint = "/api/test/fixed-window"
        
        // 첫 번째 윈도우에서 요청
        val firstWindow = runBurstRequests(endpoint, 5, 100)
            .collectList()
            .block(Duration.ofSeconds(10)) ?: emptyList()

        println("첫 번째 윈도우 완료. 20초 대기하여 윈도우 경계를 넘어가도록...")
        Thread.sleep(20000) // 20초 대기 (15초 윈도우 + 5초 여유)

        // 두 번째 윈도우에서 요청
        val secondWindow = runBurstRequests(endpoint, 5, 100)
            .collectList()
            .block(Duration.ofSeconds(10)) ?: emptyList()

        val firstAnalysis = analyzeResults(firstWindow)
        val secondAnalysis = analyzeResults(secondWindow)

        printAnalysis(firstAnalysis, "고정 윈도우 - 첫 번째 윈도우")
        printAnalysis(secondAnalysis, "고정 윈도우 - 두 번째 윈도우")

        // 두 번째 윈도우에서 더 많은 성공이 있어야 함
        StepVerifier.create(Flux.fromIterable(firstWindow + secondWindow))
            .expectNextCount((firstWindow.size + secondWindow.size).toLong())
            .verifyComplete()
    }

    @Test
    fun `고정 윈도우 - 윈도우 중간 테스트`() {
        val endpoint = "/api/test/fixed-window"
        
        // 윈도우 중간에서 요청
        Thread.sleep(8000) // 8초 대기 (15초 윈도우의 중간 정도)

        val results = runBurstRequests(endpoint, 4, 100)
            .collectList()
            .block(Duration.ofSeconds(10)) ?: emptyList()

        val analysis = analyzeResults(results)
        printAnalysis(analysis, "고정 윈도우 - 윈도우 중간 테스트")

        StepVerifier.create(Flux.fromIterable(results))
            .expectNextCount(results.size.toLong())
            .verifyComplete()
    }

    @Test
    fun `고정 윈도우 - 동시 요청 테스트`() {
        val endpoint = "/api/test/fixed-window"
        val requestCount = 10
        val concurrency = 3

        val results = runConcurrentRequests(endpoint, requestCount, concurrency)
            .collectList()
            .block(Duration.ofSeconds(30)) ?: emptyList()

        val analysis = analyzeResults(results)
        printAnalysis(analysis, "고정 윈도우 - 동시 요청 테스트")

        // 동시 요청은 윈도우 제한을 초과할 수 있음
        StepVerifier.create(Flux.fromIterable(results))
            .expectNextCount(requestCount.toLong())
            .verifyComplete()
    }

    @Test
    fun `고정 윈도우 - 지속적 부하 테스트`() {
        val endpoint = "/api/test/fixed-window"
        val durationSeconds = 25L // 윈도우(15초)보다 긴 시간
        val requestsPerSecond = 1

        val results = runSustainedLoad(endpoint, durationSeconds, requestsPerSecond)
            .collectList()
            .block(Duration.ofSeconds(35)) ?: emptyList()

        val analysis = analyzeResults(results)
        printAnalysis(analysis, "고정 윈도우 - 지속적 부하 테스트")

        // 지속적 부하는 윈도우 경계에서 리셋됨
        StepVerifier.create(Flux.fromIterable(results))
            .expectNextCount((durationSeconds * requestsPerSecond).toLong())
            .verifyComplete()
    }
} 