# Rate Limiter

Redis와 다양한 알고리즘을 활용해 애플리케이션 레벨에서 Rate Limiter를 구현하는 프로젝트입니다.

## 구현된 알고리즘

### 1. 토큰 버킷 (Token Bucket)
- **특징**: 고정된 속도로 토큰이 채워지고, 요청 시 토큰을 소비
- **장점**: 버스트 트래픽을 허용하면서도 평균 속도를 제한
- **사용 사례**: API 호출 제한, 대역폭 제어

### 2. 고정 윈도우 카운터 (Fixed Window Counter)
- **특징**: 고정된 시간 윈도우 내에서 요청 수를 카운트
- **장점**: 구현이 간단하고 메모리 사용량이 적음
- **단점**: 윈도우 경계에서 트래픽 스파이크 발생 가능

### 3. 슬라이딩 윈도우 로그 (Sliding Window Log)
- **특징**: 각 요청의 타임스탬프를 저장하여 정확한 윈도우 계산
- **장점**: 정확한 윈도우 계산으로 트래픽 스파이크 방지
- **단점**: 메모리 사용량이 상대적으로 많음

## 사용 기술 스택
- Kotlin
- Spring Boot 3.5.0
- Spring Data Redis
- Spring AOP (Annotation 기반)
- Redis (로컬 또는 원격)
- WebFlux (부하 테스트용)

## 시작하기

### 1. Redis 설치 및 실행
```bash
# macOS (Homebrew)
brew install redis
brew services start redis

# 또는 Docker 사용
docker run -d -p 6379:6379 redis:alpine
```

### 2. 애플리케이션 실행
```bash
./gradlew bootRun
```

### 3. API 테스트

#### 토큰 버킷 테스트 (5개 요청/60초)
```bash
curl http://localhost:8080/api/test/token-bucket
```

#### 고정 윈도우 테스트 (3개 요청/30초)
```bash
curl http://localhost:8080/api/test/fixed-window
```

#### 슬라이딩 윈도우 테스트 (4개 요청/45초)
```bash
curl http://localhost:8080/api/test/sliding-window
```

#### 커스텀 키 테스트 (2개 요청/60초)
```bash
curl http://localhost:8080/api/test/custom-key
```

## 부하 테스트

### JUnit 테스트 실행
```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스만 실행
./gradlew test --tests TokenBucketLoadTest
./gradlew test --tests FixedWindowLoadTest
./gradlew test --tests SlidingWindowLoadTest
./gradlew test --tests ComparisonLoadTest
```

### 부하 테스트 시나리오

#### 1. 토큰 버킷 부하 테스트
- **버스트 트래픽**: 갑작스러운 많은 요청 처리 능력
- **지속적 부하**: 연속적인 요청 처리 능력
- **토큰 리필**: 토큰 소진 후 리필 과정 테스트

#### 2. 고정 윈도우 부하 테스트
- **윈도우 경계**: 윈도우 경계에서의 동작 확인
- **동시 요청**: 동시에 들어오는 요청 처리
- **지속적 부하**: 윈도우 리셋 시점 확인

#### 3. 슬라이딩 윈도우 부하 테스트
- **정확한 윈도우 계산**: 겹치는 윈도우에서의 정확성
- **점진적 부하**: 단계별로 증가하는 부하 처리
- **윈도우 경계**: 윈도우 완전 경과 후 동작

#### 4. 알고리즘 비교 테스트
- **동일 조건 비교**: 같은 부하 조건에서의 성능 비교
- **버스트 처리 능력**: 각 알고리즘의 버스트 허용률 비교
- **복합 시나리오**: 실제 환경과 유사한 복합 부하 테스트

### 독립 실행형 부하 테스트
```bash
# 전체 부하 테스트 실행
./gradlew bootRun --args="--spring.profiles.active=stress-test"

# 특정 알고리즘만 테스트
./gradlew bootRun --args="--spring.profiles.active=stress-test token-bucket"
./gradlew bootRun --args="--spring.profiles.active=stress-test fixed-window"
./gradlew bootRun --args="--spring.profiles.active=stress-test sliding-window"
./gradlew bootRun --args="--spring.profiles.active=stress-test comparison"
```

### 부하 테스트 결과 예시
```
=== 토큰 버킷 - 버스트 트래픽 테스트 ===
총 요청 수: 10
성공 요청 수: 5
실패 요청 수: 5
Rate Limited 요청 수: 5
성공률: 50.00%
Rate Limit 비율: 50.00%
평균 응답 시간: 45.20ms
========================

=== 알고리즘 성능 비교 ===
토큰 버킷:
  - 성공률: 50.00%
  - Rate Limit 비율: 50.00%
  - 평균 응답 시간: 45.20ms
고정 윈도우:
  - 성공률: 30.00%
  - Rate Limit 비율: 70.00%
  - 평균 응답 시간: 42.10ms
슬라이딩 윈도우:
  - 성공률: 25.00%
  - Rate Limit 비율: 75.00%
  - 평균 응답 시간: 48.50ms
========================
```

## 사용법

### 어노테이션 기반 Rate Limiting
```kotlin
@GetMapping("/api/resource")
@RateLimit(
    type = RateLimiterType.TOKEN_BUCKET,
    limit = 10,
    windowSeconds = 60
)
fun getResource(): ResponseEntity<String> {
    return ResponseEntity.ok("Resource accessed successfully")
}
```

### Rate Limiter 타입
- `TOKEN_BUCKET`: 토큰 버킷 알고리즘
- `FIXED_WINDOW`: 고정 윈도우 카운터
- `SLIDING_WINDOW`: 슬라이딩 윈도우 로그

### 키 생성 전략
1. **커스텀 키**: `key` 파라미터로 직접 지정
2. **IP 기반**: 클라이언트 IP + HTTP 메서드 + 경로
3. **메서드 기반**: 클래스명 + 메서드명

## Rate Limit 초과 시 응답
```json
{
  "error": "Rate Limit Exceeded",
  "message": "Rate limit exceeded for key: 127.0.0.1:GET:/api/test/token-bucket",
  "status": 429
}
```

## 프로젝트 구조
```
src/main/kotlin/study/ratelimiter/
├── annotation/
│   └── RateLimit.kt
├── aspect/
│   └── RateLimitAspect.kt
├── config/
│   └── RedisConfig.kt
├── controller/
│   └── TestController.kt
├── exception/
│   └── GlobalExceptionHandler.kt
├── ratelimiter/
│   ├── RateLimiter.kt
│   ├── RateLimiterFactory.kt
│   ├── RateLimiterType.kt
│   ├── TokenBucketRateLimiter.kt
│   ├── FixedWindowRateLimiter.kt
│   └── SlidingWindowRateLimiter.kt
└── service/
    └── LoadTestService.kt

src/test/kotlin/study/ratelimiter/
├── LoadTestScenario.kt
├── TokenBucketLoadTest.kt
├── FixedWindowLoadTest.kt
├── SlidingWindowLoadTest.kt
├── ComparisonLoadTest.kt
└── StressTestRunner.kt
```