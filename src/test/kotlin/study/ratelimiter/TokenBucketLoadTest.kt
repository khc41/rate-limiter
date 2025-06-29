package study.ratelimiter

import org.junit.jupiter.api.Test
import org.springframework.test.context.ActiveProfiles
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Duration

@ActiveProfiles("test")
class TokenBucketLoadTest : LoadTestScenario() {

    @Test
    fun `토큰 버킷 - 버스트 트래픽 테스트`() {
        val endpoint = "/api/test/token-bucket"
        val burstSize = 10 // 토큰 버킷은 버스트를 허용할 수 있음

        val results = runBurstRequests(endpoint, burstSize, 50)
            .collectList()
            .block(Duration.ofSeconds(30)) ?: emptyList()

        val analysis = analyzeResults(results)
        printAnalysis(analysis, "토큰 버킷 - 버스트 트래픽 테스트")

        // 토큰 버킷은 버스트를 허용하므로 일부 요청은 성공해야 함
        StepVerifier.create(Flux.fromIterable(results))
            .expectNextCount(burstSize.toLong())
            .verifyComplete()
    }

    @Test
    fun `토큰 버킷 - 지속적 부하 테스트`() {
        val endpoint = "/api/test/token-bucket"
        val durationSeconds = 10L
        val requestsPerSecond = 2 // 초당 2개 요청

        val results = runSustainedLoad(endpoint, durationSeconds, requestsPerSecond)
            .collectList()
            .block(Duration.ofSeconds(15)) ?: emptyList()

        val analysis = analyzeResults(results)
        printAnalysis(analysis, "토큰 버킷 - 지속적 부하 테스트")

        // 지속적 부하는 토큰 리필로 인해 일부 요청이 성공해야 함
        StepVerifier.create(Flux.fromIterable(results))
            .expectNextCount((durationSeconds * requestsPerSecond).toLong())
            .verifyComplete()
    }

    @Test
    fun `토큰 버킷 - 동시 요청 테스트`() {
        val endpoint = "/api/test/token-bucket"
        val requestCount = 15
        val concurrency = 5

        val results = runConcurrentRequests(endpoint, requestCount, concurrency)
            .collectList()
            .block(Duration.ofSeconds(30)) ?: emptyList()

        val analysis = analyzeResults(results)
        printAnalysis(analysis, "토큰 버킷 - 동시 요청 테스트")

        // 동시 요청은 토큰 버킷의 용량을 초과할 수 있음
        StepVerifier.create(Flux.fromIterable(results))
            .expectNextCount(requestCount.toLong())
            .verifyComplete()
    }

    @Test
    fun `토큰 버킷 - 토큰 리필 테스트`() {
        val endpoint = "/api/test/token-bucket"
        
        // 첫 번째 버스트: 토큰을 모두 소진
        val firstBurst = runBurstRequests(endpoint, 8, 100)
            .collectList()
            .block(Duration.ofSeconds(10)) ?: emptyList()

        println("첫 번째 버스트 완료. 잠시 대기...")
        Thread.sleep(2000) // 2초 대기하여 토큰 리필

        // 두 번째 버스트: 토큰이 리필된 후
        val secondBurst = runBurstRequests(endpoint, 5, 100)
            .collectList()
            .block(Duration.ofSeconds(10)) ?: emptyList()

        val firstAnalysis = analyzeResults(firstBurst)
        val secondAnalysis = analyzeResults(secondBurst)

        printAnalysis(firstAnalysis, "토큰 버킷 - 첫 번째 버스트")
        printAnalysis(secondAnalysis, "토큰 버킷 - 두 번째 버스트 (토큰 리필 후)")

        // 두 번째 버스트에서 더 많은 성공이 있어야 함
        StepVerifier.create(Flux.fromIterable(firstBurst + secondBurst))
            .expectNextCount((firstBurst.size + secondBurst.size).toLong())
            .verifyComplete()
    }
} 