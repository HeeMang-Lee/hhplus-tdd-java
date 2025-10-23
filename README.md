# 포인트 관리 시스템 (TDD 기반)

## 프로젝트 개요

이 프로젝트는 **TDD(Test-Driven Development) 방법론**을 적용하여 구현한 포인트 관리 시스템입니다. 사용자의 포인트 충전, 사용, 조회 및 내역 관리 기능을 제공하며, **동시성 제어**를 통해 데이터 정합성을 보장합니다.

## 기술 스택

- **Java 17**
- **Spring Boot 3.2.0**
- **Gradle 8.4**
- **JUnit 5** - 단위 테스트 및 통합 테스트
- **Mockito** - Mock 기반 단위 테스트
- **AssertJ** - assertion API

## 핵심 기능

### 1. 포인트 조회
- 특정 유저의 현재 포인트를 조회합니다.

### 2. 포인트 충전
- **최소 충전 금액**: 1,000원
- **충전 단위**: 1,000원 단위만 가능
- 충전 시 포인트가 증가하고 내역이 기록됩니다.

### 3. 포인트 사용
- **최소 사용 단위**: 100원 단위
- **잔고 부족 시**: `BalanceInsufficientException` 발생
- 사용 시 포인트가 감소하고 내역이 기록됩니다.

### 4. 포인트 내역 조회
- 특정 유저의 모든 포인트 충전/사용 내역을 조회합니다.

---

## 동시성 제어 방식

### 문제 정의
여러 스레드가 동시에 같은 유저의 포인트를 충전하거나 사용할 때 **Race Condition**이 발생하여 데이터 정합성이 깨질 수 있습니다.

### 해결 방안: 유저별 락(Lock) 관리

```java
@Service
public class PointService {
    private final ConcurrentHashMap<Long, Object> userLocks = new ConcurrentHashMap<>();

    private Object getUserLock(long userId) {
        return userLocks.computeIfAbsent(userId, key -> new Object());
    }

    public UserPoint chargePoint(long userId, long amount) {
        synchronized (getUserLock(userId)) {
            // 포인트 조회 -> 업데이트 -> 내역 기록이 원자적으로 실행됨
            UserPoint currentPoint = userPointTable.selectById(userId);
            long newPoint = currentPoint.point() + amount;
            UserPoint updatedPoint = userPointTable.insertOrUpdate(userId, newPoint);
            pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, updatedPoint.updateMillis());
            return updatedPoint;
        }
    }
}
```

### 동시성 제어 특징

1. **유저별 독립적인 락**
   - `ConcurrentHashMap`으로 유저별 락 객체를 관리
   - 다른 유저의 거래는 서로 영향을 주지 않음 (병렬성 확보)

2. **동일 유저에 대한 순차 처리**
   - `getUserLock(userId)`로 동일한 락 객체를 반환
   - 동일 유저의 충전/사용이 동시에 발생해도 순차적으로 처리됨

3. **트랜잭션 일관성**
   - 포인트 업데이트와 내역 기록을 하나의 synchronized 블록 안에서 처리
   - 부분적인 성공/실패를 방지

### 검증된 동시성 시나리오

- ✅ 동일 유저에 대한 동시 충전 (10개 스레드 × 1,000원)
- ✅ 동일 유저에 대한 동시 사용 (10개 스레드 × 500원)
- ✅ 동일 유저에 대한 충전+사용 혼합 요청 (5+5개 스레드)
- ✅ 다중 스레드 내역 기록 정확성 및 중복 방지
- ✅ 대량 거래 부하 (100개 스레드 × 100건)

---

## 예외 처리

### 1. 입력 값 검증 (`Bean Validation`)

#### 충전 요청 검증
```java
public record ChargeRequest(
    @Min(value = 1000, message = "충전 금액은 최소 1000원 이상이어야 합니다")
    @MultipleOf(value = 1000, message = "충전 금액은 1000원 단위여야 합니다")
    long amount
) {}
```

#### 사용 요청 검증
```java
public record UseRequest(
    @Positive(message = "사용 금액은 0보다 커야 합니다")
    @MultipleOf(value = 100, message = "사용 금액은 100원 단위여야 합니다")
    long amount
) {}
```

#### 커스텀 Validator: `@MultipleOf`
```java
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MultipleOfValidator.class)
public @interface MultipleOf {
    String message() default "값은 {value}의 배수여야 합니다";
    long value();
}

public class MultipleOfValidator implements ConstraintValidator<MultipleOf, Long> {
    private long multipleOf;

    @Override
    public void initialize(MultipleOf constraintAnnotation) {
        this.multipleOf = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(Long value, ConstraintValidatorContext context) {
        if (value == null) return true;
        return value % multipleOf == 0;
    }
}
```

### 2. 비즈니스 로직 검증

#### 잔고 부족 예외
```java
public class BalanceInsufficientException extends RuntimeException {
    public BalanceInsufficientException(String message) {
        super(message);
    }
}
```

**발생 조건**: 사용 금액이 보유 포인트보다 큰 경우

```java
if (currentPoint.point() < amount) {
    throw new BalanceInsufficientException("잔고가 부족합니다.");
}
```

### 3. 전역 예외 처리 (`@RestControllerAdvice`)

```java
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        ErrorResponse response = new ErrorResponse("ValidationError", "잘못된 요청 값입니다.");
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(BalanceInsufficientException.class)
    public ResponseEntity<ErrorResponse> handleBalanceInsufficientException(BalanceInsufficientException e) {
        ErrorResponse response = new ErrorResponse("BalanceInsufficientException", e.getMessage());
        return ResponseEntity.badRequest().body(response);
    }
}
```

**ErrorResponse 형식**:
```json
{
  "code": "ValidationError",
  "message": "잘못된 요청 값입니다."
}
```

---

## TDD 개발 프로세스

이 프로젝트는 **모든 기능을 RED-GREEN-REFACTOR 사이클**로 개발했습니다.

### RED-GREEN-REFACTOR 사이클

1. **RED**: 실패하는 테스트를 먼저 작성
2. **GREEN**: 최소한의 코드(하드코딩 포함)로 테스트 통과
3. **REFACTOR**: 중복 제거 및 코드 개선

### TDD 사이클별 구현 내역

#### 1. 기본 CRUD 기능 (RED-GREEN-REFACTOR)

**RED**:
```java
@Test
@DisplayName("특정 유저의 포인트를 조회한다")
void getUserPoint() {
    // given
    long userId = 1L;
    UserPoint expectedPoint = new UserPoint(userId, 1000L, FIXED_TIME);
    when(userPointTable.selectById(userId)).thenReturn(expectedPoint);

    // when
    UserPoint result = pointService.getUserPoint(userId);

    // then
    assertThat(result.point()).isEqualTo(1000L);
}
```

**GREEN**:
```java
public UserPoint getUserPoint(long userId) {
    return userPointTable.selectById(userId);
}
```

**REFACTOR**: (이미 최소 구현이므로 생략)

---

#### 2. 포인트 충전 (RED-GREEN-REFACTOR)

**RED**:
```java
@Test
@DisplayName("포인트를 충전한다")
void chargePoint() {
    // given
    long userId = 1L;
    long currentAmount = 1000L;
    long chargeAmount = 500L;
    long expectedAmount = 1500L;

    // when
    UserPoint result = pointService.chargePoint(userId, chargeAmount);

    // then
    assertThat(result.point()).isEqualTo(expectedAmount);
}
```

**GREEN**:
```java
public UserPoint chargePoint(long userId, long amount) {
    // 하드코딩으로 최소 통과
    if (userId == 1L && amount == 500L) {
        return new UserPoint(userId, 1500L, System.currentTimeMillis());
    }
    return null;
}
```

**REFACTOR**:
```java
public UserPoint chargePoint(long userId, long amount) {
    UserPoint currentPoint = userPointTable.selectById(userId);
    long newPoint = currentPoint.point() + amount;
    UserPoint updatedPoint = userPointTable.insertOrUpdate(userId, newPoint);
    pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, updatedPoint.updateMillis());
    return updatedPoint;
}
```

---

#### 3. 최소 충전 금액 검증 (RED-GREEN-REFACTOR)

**RED**:
```java
@Test
@DisplayName("충전 금액이 1000원 미만이면 400 Bad Request 반환")
void chargePoint_invalidAmount_lessThan1000_returnsBadRequest() throws Exception {
    mockMvc.perform(patch("/point/{id}/charge", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\":500}"))
            .andExpect(status().isBadRequest());
}
```

**GREEN**:
```java
@PatchMapping("{id}/charge")
public UserPoint charge(@PathVariable long id, @RequestBody ChargeRequest request) {
    // 하드코딩: 1000원 미만이면 예외 발생
    if (request.amount() < 1000) {
        throw new IllegalArgumentException("최소 1000원");
    }
    return pointService.chargePoint(id, request.amount());
}
```

**REFACTOR**:
```java
public record ChargeRequest(
    @Min(value = 1000, message = "충전 금액은 최소 1000원 이상이어야 합니다")
    @MultipleOf(value = 1000, message = "충전 금액은 1000원 단위여야 합니다")
    long amount
) {}
```

---

#### 4. 잔고 부족 예외 (RED-GREEN-REFACTOR)

**RED**:
```java
@Test
@DisplayName("사용 금액이 보유 포인트보다 크면 예외가 발생한다")
void usePoint_insufficientBalance() {
    // given
    long userId = 1L;
    long currentAmount = 500L;
    long useAmount = 1000L;

    // when & then
    assertThatThrownBy(() -> pointService.usePoint(userId, useAmount))
            .isInstanceOf(BalanceInsufficientException.class)
            .hasMessage("잔고가 부족합니다.");
}
```

**GREEN**:
```java
public UserPoint usePoint(long userId, long amount) {
    // 하드코딩: 특정 값에서만 예외 발생
    if (userId == 1L && amount == 1000L) {
        throw new BalanceInsufficientException("잔고가 부족합니다.");
    }
    return null;
}
```

**REFACTOR**:
```java
public UserPoint usePoint(long userId, long amount) {
    UserPoint currentPoint = userPointTable.selectById(userId);

    if (currentPoint.point() < amount) {
        throw new BalanceInsufficientException("잔고가 부족합니다.");
    }

    long newPoint = currentPoint.point() - amount;
    UserPoint updatedPoint = userPointTable.insertOrUpdate(userId, newPoint);
    pointHistoryTable.insert(userId, amount, TransactionType.USE, updatedPoint.updateMillis());
    return updatedPoint;
}
```

---

#### 5. 동시성 제어 - 충전 (RED-GREEN-REFACTOR)

**RED**:
```java
@Test
@DisplayName("동일 유저에 대해 동시에 포인트를 충전하면 최종 금액이 정확해야 한다")
void chargePoint_concurrency() throws InterruptedException {
    // given
    long userId = 1L;
    int threadCount = 10;
    long chargeAmount = 1000L;
    long expectedFinalAmount = 10000L;

    // when
    ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
        executorService.execute(() -> {
            try {
                pointService.chargePoint(userId, chargeAmount);
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await();
    executorService.shutdown();

    // then
    assertThat(finalAmount).isEqualTo(expectedFinalAmount);
}
```

**GREEN**:
```java
public UserPoint chargePoint(long userId, long amount) {
    // 하드코딩: userId가 1L일 때만 락 적용
    if (userId == 1L) {
        synchronized (getUserLock(userId)) {
            UserPoint currentPoint = userPointTable.selectById(userId);
            long newPoint = currentPoint.point() + amount;
            UserPoint updatedPoint = userPointTable.insertOrUpdate(userId, newPoint);
            pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, updatedPoint.updateMillis());
            return updatedPoint;
        }
    }

    // 락 없는 코드 (다른 userId)
    UserPoint currentPoint = userPointTable.selectById(userId);
    long newPoint = currentPoint.point() + amount;
    UserPoint updatedPoint = userPointTable.insertOrUpdate(userId, newPoint);
    pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, updatedPoint.updateMillis());
    return updatedPoint;
}
```

**REFACTOR**:
```java
private final ConcurrentHashMap<Long, Object> userLocks = new ConcurrentHashMap<>();

private Object getUserLock(long userId) {
    return userLocks.computeIfAbsent(userId, key -> new Object());
}

public UserPoint chargePoint(long userId, long amount) {
    synchronized (getUserLock(userId)) {
        UserPoint currentPoint = userPointTable.selectById(userId);
        long newPoint = currentPoint.point() + amount;
        UserPoint updatedPoint = userPointTable.insertOrUpdate(userId, newPoint);
        pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, updatedPoint.updateMillis());
        return updatedPoint;
    }
}
```

---

### 개발한 단위 테스트 목록

#### PointServiceTest (Mock 기반)
1. ✅ `getUserPoint()` - 특정 유저 포인트 조회
2. ✅ `chargePoint()` - 포인트 충전
3. ✅ `usePoint()` - 포인트 사용
4. ✅ `getPointHistory()` - 포인트 내역 조회
5. ✅ `usePoint_insufficientBalance()` - 잔고 부족 예외
6. ✅ `chargePoint_concurrency()` - 동시 충전 동시성 제어
7. ✅ `usePoint_concurrency()` - 동시 사용 동시성 제어
8. ✅ `chargeAndUsePoint_concurrency()` - 충전+사용 혼합 동시성 제어
9. ✅ `pointHistory_concurrency()` - 내역 기록 정확성 및 중복 방지

#### PointControllerTest (MockMvc 기반)
1. ✅ `charge()` - 충전 API
2. ✅ `use()` - 사용 API
3. ✅ `charge_invalidAmount_lessThan1000()` - 최소 충전 금액 검증
4. ✅ `charge_invalidAmount_notMultipleOf1000()` - 1000원 단위 검증
5. ✅ `use_invalidAmount_notMultipleOf100()` - 100원 단위 검증

---

## 테스트 가능 구조 설계

### 1. 의존성 주입 (Dependency Injection)

모든 의존성을 생성자를 통해 주입하여 테스트에서 Mock 객체로 교체 가능:

```java
@Service
public class PointService {
    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }
}
```

### 2. 시간 고정 (Idempotent Tests)

시간 의존성을 제거하여 테스트를 멱등하게 만듦:

```java
private static final long FIXED_TIME = 1234567890L;

@Test
void chargePoint() {
    // given
    when(userPointTable.insertOrUpdate(userId, newAmount))
        .thenReturn(new UserPoint(userId, newAmount, FIXED_TIME));

    // then
    verify(pointHistoryTable).insert(eq(userId), eq(amount), eq(TransactionType.CHARGE), eq(FIXED_TIME));
}
```

### 3. Mock vs 실제 객체 분리

- **단위 테스트**: Mock 객체 사용 (`@Mock`, `@InjectMocks`)
- **통합 테스트**: 실제 Spring Context 사용 (`@SpringBootTest`, `@AutoConfigureMockMvc`)

### 4. 격리된 테스트 환경

각 테스트가 서로 영향을 주지 않도록 독립적인 userId 사용:

```java
// 단일 사용자 흐름 테스트
long userId = 99L;

// 대량 거래 부하 테스트
long userId = 999L;
```

### 5. 동시성 테스트 구조

`ExecutorService`, `CountDownLatch`, `AtomicLong`을 활용한 실제 동시성 시뮬레이션:

```java
ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
CountDownLatch latch = new CountDownLatch(threadCount);
AtomicLong currentAmount = new AtomicLong(initialAmount);

// 스레드 실행
for (int i = 0; i < threadCount; i++) {
    executorService.execute(() -> {
        try {
            pointService.chargePoint(userId, amount);
        } finally {
            latch.countDown();
        }
    });
}

latch.await();
executorService.shutdown();
executorService.awaitTermination(10, TimeUnit.SECONDS);
```

---

## 통합 테스트

통합 테스트는 **실제 Spring Context를 띄우고 MockMvc를 통해 HTTP 요청을 시뮬레이션**하여 전체 시스템의 동작을 검증합니다.

### PointControllerIntegrationTest

#### 1. 입력 검증 통합 테스트

```java
@Test
@DisplayName("충전 금액이 0이면 ErrorResponse 반환")
void chargePoint_invalidAmount_zero_returnsErrorResponse() throws Exception {
    mockMvc.perform(patch("/point/{id}/charge", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\":0}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ValidationError"))
            .andExpect(jsonPath("$.message").value("잘못된 요청 값입니다."));
}
```

**검증 내용**:
- Bean Validation이 올바르게 동작하는가?
- GlobalExceptionHandler가 예외를 잡아 ErrorResponse로 변환하는가?

---

#### 2. 단일 사용자 포인트 흐름 시나리오

```java
@Test
@DisplayName("단일 사용자 포인트 흐름: 조회 -> 충전 -> 사용 -> 내역조회가 일관된 상태를 유지한다")
void singleUserPointFlow_maintainsConsistentState() throws Exception {
    // given
    long userId = 99L;
    long chargeAmount = 5000L;
    long useAmount = 2000L;

    // 1. 초기 포인트 조회
    MvcResult initialResult = mockMvc.perform(get("/point/{id}", userId))
            .andExpect(status().isOk())
            .andReturn();
    long initialPoint = extractPointFromResponse(initialResult.getResponse().getContentAsString());

    // 2. 포인트 충전
    mockMvc.perform(patch("/point/{id}/charge", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\":" + chargeAmount + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.point").value(initialPoint + chargeAmount));

    // 3. 포인트 사용
    mockMvc.perform(patch("/point/{id}/use", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\":" + useAmount + "}"))
            .andExpect(status().isOk());

    // 4. 최종 포인트 검증
    long expectedFinalPoint = initialPoint + chargeAmount - useAmount;
    mockMvc.perform(get("/point/{id}", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.point").value(expectedFinalPoint));

    // 5. 내역 검증
    mockMvc.perform(get("/point/{id}/histories", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].type").value("CHARGE"))
            .andExpect(jsonPath("$[1].type").value("USE"));
}
```

**검증 내용**:
- 전체 API 흐름이 일관성을 유지하는가?
- 각 단계에서 포인트 금액이 정확하게 반영되는가?
- 내역이 올바르게 기록되는가?

---

#### 3. 대량 거래 부하 시나리오

```java
@Test
@DisplayName("대량 거래 부하 시나리오: 수백 건의 동시 거래에도 데이터 정합성이 유지된다")
void massiveTransactions_maintainDataIntegrity() throws Exception {
    // given
    long userId = 999L;
    long initialAmount = 100000L;
    int chargeThreadCount = 50; // 충전 50건
    int useThreadCount = 50; // 사용 50건
    int totalThreadCount = 100;
    long chargeAmount = 1000L;
    long useAmount = 500L;

    // 초기 포인트 설정
    mockMvc.perform(patch("/point/{id}/charge", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\":" + initialAmount + "}"))
            .andExpect(status().isOk());

    // when - 100개 스레드로 동시 거래 수행
    ExecutorService executorService = Executors.newFixedThreadPool(totalThreadCount);
    CountDownLatch latch = new CountDownLatch(totalThreadCount);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);

    // 충전 스레드 50개
    for (int i = 0; i < chargeThreadCount; i++) {
        executorService.execute(() -> {
            try {
                mockMvc.perform(patch("/point/{id}/charge", userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"amount\":" + chargeAmount + "}"))
                        .andExpect(status().isOk());
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });
    }

    // 사용 스레드 50개
    for (int i = 0; i < useThreadCount; i++) {
        executorService.execute(() -> {
            try {
                mockMvc.perform(patch("/point/{id}/use", userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"amount\":" + useAmount + "}"))
                        .andExpect(status().isOk());
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await();
    executorService.shutdown();
    executorService.awaitTermination(30, TimeUnit.SECONDS);

    // then
    // 1. 최종 포인트 금액 검증
    long expectedFinalPoint = initialAmount + (chargeAmount * chargeThreadCount) - (useAmount * useThreadCount);
    // 100000 + (1000 * 50) - (500 * 50) = 125000
    MvcResult finalResult = mockMvc.perform(get("/point/{id}", userId))
            .andExpect(status().isOk())
            .andReturn();

    long finalPoint = extractPointFromResponse(finalResult.getResponse().getContentAsString());
    assertThat(finalPoint).isEqualTo(expectedFinalPoint);

    // 2. 거래 내역 검증 - 모든 101건이 기록되었는지 확인
    MvcResult historyResult = mockMvc.perform(get("/point/{id}/histories", userId))
            .andExpect(status().isOk())
            .andReturn();

    int historyCount = countJsonArrayElements(historyResult.getResponse().getContentAsString());
    int expectedHistoryCount = 1 + totalThreadCount; // 초기 충전 1건 + 대량 거래 100건
    assertThat(historyCount).isEqualTo(expectedHistoryCount);

    // 3. 성공/실패 카운트 확인
    assertThat(successCount.get()).isEqualTo(totalThreadCount);
    assertThat(failureCount.get()).isEqualTo(0);
}
```

**검증 내용**:
- 100개의 동시 요청이 모두 성공하는가?
- 최종 포인트 금액이 정확한가? (125,000원)
- 모든 거래 내역이 누락 없이 기록되었는가? (101건)
- 실제 부하 상황에서도 동시성 제어가 올바르게 동작하는가?

---

## Claude Code 활용 전략

이 프로젝트는 **Claude Code를 활용하여 효율적인 TDD 개발 프로세스**를 구축했습니다.

### 1. 컨텍스트 전달 최적화

#### `@` 멘션을 통한 정확한 컨텍스트 제공

```
@PointService.java @PointServiceTest.java
다음으로는 동시성 포인트 사용을 해결해보자
```

**효과**:
- Claude Code가 정확히 어떤 파일을 수정해야 하는지 알 수 있음
- 불필요한 파일 탐색 시간 절약
- 기존 코드 컨텍스트를 이해하고 일관된 스타일로 작성

---

### 2. TDD 사이클 자동화 프롬프트

#### 명확한 단계 지시

```
동시성 포인트 사용을 TDD 사이클로 해결해보자
RED부터 시작해서 커밋하고, GREEN 구현하고 커밋하고, REFACTOR 하고 커밋해줘
```

**효과**:
- Claude Code가 RED-GREEN-REFACTOR를 명확히 구분하여 작업
- 각 단계마다 자동으로 테스트 실행 후 커밋
- 커밋 메시지도 단계별로 명확하게 작성됨

---

### 3. Git 자동화 워크플로우

#### 작업 완료 후 자동 커밋/푸시

```bash
git add -A && git commit -m "$(cat <<'EOF'
test: 동시성 포인트 사용 테스트 추가 (RED)

- 동일 유저에 대해 동시 포인트 사용 시 정확한 최종 금액 검증
- 10개 스레드가 각각 500원씩 사용, 최종 5000원 예상
- AtomicLong으로 DB 상태 시뮬레이션
- 락 없이 Race Condition 발생으로 테스트 실패

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)" && git push origin step2
```

**설계된 워크플로우**:
1. Claude Code가 코드 작성 완료
2. 테스트 실행하여 성공 확인
3. 사용자가 검토 및 승인
4. 자동으로 `git add`, `git commit`, `git push` 실행
5. 다음 단계(GREEN)로 진행

**장점**:
- 수동 커밋 작업 불필요
- 커밋 메시지가 일관되고 상세함
- 각 TDD 단계가 명확하게 히스토리에 남음
- 작업 흐름이 끊기지 않음

---

### 4. 프롬프트 패턴

#### 효과적인 프롬프트 예시

```
# 초기 컨텍스트 설정
@PointService.java @PointServiceTest.java
동시성 충전 문제를 TDD로 해결해줘

# 단계별 명확한 지시
레드부터 해야하고, 레드 커밋하고 그린 커밋하고 이런 식으로 해야해

# 구체적인 구현 방향 제시
그린은 앞으로 최소 통과 기준으로만 작성해줘, 하드코딩해서라도

# 리팩터링 방향 제시
리팩터 구현할 때는 ConcurrentHashMap을 이용해서 해줘
락을 사용자별로 관리해서 병렬성과 안정성을 확보할 수 있도록
```

---

### 5. Claude Code의 자율적 판단 허용

```
다음은 통합테스트를 구현할건데 단일 사용자 포인트 흐름 시나리오로
조회 -> 충전 -> 사용 -> 내역조회를 수행했을 때
모든 API가 일관된 상태를 유지하는지 검증해보자
이건 TDD로 안해도 되는건가? 통합테스트는 시스템 검증이 목표니까
```

**Claude Code 응답**:
> "맞습니다! 통합테스트는 TDD 사이클로 할 필요가 없습니다.
> 통합테스트는 이미 구현된 시스템의 전체적인 동작을 검증하는 것이 목표이므로,
> 바로 테스트를 작성하고 실행하면 됩니다."

**효과**:
- Claude Code가 상황에 맞는 최적의 접근법을 제안
- 불필요한 작업을 피하고 효율적으로 개발
- 개발자의 의도를 이해하고 협업

---

### 6. 피드백 기반 개선

```
# 사용자 피드백
테스트 의도는 좋지만 Red 단계에서는 정상 동작을 검증하기보단
실패를 명시적으로 기대하는 형태가 더 적절해
테스트 이름을 '정확해야 한다' → '일관되지 않을 수 있다'로 바꾸고
assertThat(...).isNotEqualTo(10000L)처럼 락이 없을 때 깨지는 상황을
드러내면 더 직관적인 Red 테스트가 돼
```

**Claude Code 대응**:
- 피드백을 즉시 반영하여 테스트 개선
- 더 명확한 RED 테스트로 수정
- TDD 철학에 맞는 테스트 작성 학습

---

## 테스트 실행

### 전체 테스트 실행

```bash
./gradlew test
```

### 특정 테스트 클래스 실행

```bash
./gradlew test --tests "io.hhplus.tdd.point.PointServiceTest"
./gradlew test --tests "io.hhplus.tdd.point.PointControllerTest"
./gradlew test --tests "io.hhplus.tdd.point.PointControllerIntegrationTest"
```

### 특정 테스트 메서드 실행

```bash
./gradlew test --tests "io.hhplus.tdd.point.PointServiceTest.chargePoint_concurrency"
```

### 빌드 및 테스트

```bash
./gradlew clean build
```

---

## 프로젝트 구조

```
src/
├── main/
│   └── java/io/hhplus/tdd/
│       ├── TddApplication.java
│       ├── GlobalExceptionHandler.java           # 전역 예외 처리
│       ├── ErrorResponse.java                    # 에러 응답 DTO
│       ├── database/
│       │   ├── PointHistoryTable.java           # 포인트 내역 저장소
│       │   └── UserPointTable.java              # 유저 포인트 저장소
│       └── point/
│           ├── PointController.java              # REST API Controller
│           ├── PointService.java                 # 비즈니스 로직 (동시성 제어)
│           ├── ChargeRequest.java                # 충전 요청 DTO (검증 포함)
│           ├── UseRequest.java                   # 사용 요청 DTO (검증 포함)
│           ├── MultipleOf.java                   # 커스텀 Validator (배수 검증)
│           ├── MultipleOfValidator.java          # Validator 구현체
│           ├── BalanceInsufficientException.java # 잔고 부족 예외
│           ├── UserPoint.java                    # 유저 포인트 Entity
│           ├── PointHistory.java                 # 포인트 내역 Entity
│           └── TransactionType.java              # 거래 유형 (CHARGE/USE)
└── test/
    └── java/io/hhplus/tdd/point/
        ├── PointServiceTest.java                 # 서비스 단위 테스트 (Mock)
        ├── PointControllerTest.java              # Controller 단위 테스트 (MockMvc)
        └── PointControllerIntegrationTest.java   # 통합 테스트
```

---

## 주요 커밋 히스토리

모든 기능이 **RED-GREEN-REFACTOR 사이클**로 커밋되었습니다:

```
test: 동시성 포인트 충전 테스트 추가 (RED)
feat: userId가 1L일 때만 동시성 제어 (GREEN)
refactor: ConcurrentHashMap으로 사용자별 락 관리 (REFACTOR)

test: 동시성 포인트 사용 테스트 추가 (RED)
feat: userId가 1L일 때만 동시성 제어 (GREEN)
refactor: 모든 유저에 대해 동시성 제어 적용 (REFACTOR)

test: 동시성 혼합 요청(충전+사용) 테스트 추가 (RED)
feat: userId가 1L일 때만 혼합 요청 동시성 제어 (GREEN)
refactor: 모든 유저에 대해 혼합 요청 동시성 제어 (REFACTOR)

test: 동시 거래 시 내역 기록 검증 테스트 추가 (RED)
feat: userId가 1L일 때만 내역 기록 동시성 제어 (GREEN)
refactor: 모든 유저에 대해 내역 기록 동시성 제어 (REFACTOR)

test: 단일 사용자 포인트 흐름 통합테스트 추가
test: 대량 거래 부하 시나리오 통합테스트 추가
```

---

## 학습 및 개선 사항

### TDD의 가치

1. **설계 개선**: 테스트를 먼저 작성하면서 자연스럽게 느슨한 결합과 높은 응집도를 유지
2. **리팩터링 안정성**: 언제든지 자신 있게 리팩터링 가능 (테스트가 안전망 역할)
3. **문서화**: 테스트 자체가 코드의 사용법과 의도를 명확히 보여줌
4. **버그 조기 발견**: 개발 중 즉시 문제를 발견하고 수정

### 동시성 제어의 중요성

- 단순히 `synchronized`만 붙이면 모든 유저가 직렬화되어 성능 저하
- **유저별 락 관리**로 병렬성과 안정성을 동시에 확보
- `ConcurrentHashMap.computeIfAbsent()`로 락 객체를 안전하게 관리

### 통합 테스트의 가치

- 단위 테스트만으로는 시스템 전체의 통합 문제를 발견하기 어려움
- 실제 HTTP 요청 흐름을 시뮬레이션하여 전체 시스템 검증
- 대량 부하 시나리오로 실전 환경의 문제를 미리 발견

---

## 결론

이 프로젝트는 **TDD 방법론**과 **Claude Code의 효율적인 활용**을 통해 다음을 달성했습니다:

✅ **모든 기능을 RED-GREEN-REFACTOR 사이클로 개발**
✅ **동시성 제어를 통한 데이터 정합성 보장**
✅ **입력 검증 및 예외 처리의 완전한 커버리지**
✅ **단위 테스트와 통합 테스트의 균형 잡힌 구성**
✅ **테스트 가능한 설계와 느슨한 결합**
✅ **자동화된 Git 워크플로우로 효율적인 개발 프로세스**

이를 통해 **안정적이고 확장 가능한 포인트 관리 시스템**을 구축했습니다.

