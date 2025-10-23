package io.hhplus.tdd.point;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PointControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("충전 금액이 0이면 ErrorResponse 반환")
    void chargePoint_invalidAmount_zero_returnsErrorResponse() throws Exception {
        // given
        long userId = 1L;
        long invalidAmount = 0L;

        // when & then
        mockMvc.perform(patch("/point/{id}/charge", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + invalidAmount + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ValidationError"))
                .andExpect(jsonPath("$.message").value("잘못된 요청 값입니다."));
    }

    @Test
    @DisplayName("충전 금액이 음수면 ErrorResponse 반환")
    void chargePoint_invalidAmount_negative_returnsErrorResponse() throws Exception {
        // given
        long userId = 1L;
        long invalidAmount = -100L;

        // when & then
        mockMvc.perform(patch("/point/{id}/charge", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + invalidAmount + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ValidationError"))
                .andExpect(jsonPath("$.message").value("잘못된 요청 값입니다."));
    }

    @Test
    @DisplayName("단일 사용자 포인트 흐름: 조회 -> 충전 -> 사용 -> 내역조회가 일관된 상태를 유지한다")
    void singleUserPointFlow_maintainsConsistentState() throws Exception {
        // given
        long userId = 99L; // 다른 테스트와 충돌하지 않도록 높은 ID 사용
        long chargeAmount = 5000L;
        long useAmount = 2000L;

        // 1. 초기 포인트 조회
        MvcResult initialResult = mockMvc.perform(get("/point/{id}", userId))
                .andExpect(status().isOk())
                .andReturn();

        String initialResponse = initialResult.getResponse().getContentAsString();
        long initialPoint = extractPointFromResponse(initialResponse);

        // 2. 포인트 충전
        String chargeRequest = String.format("{\"amount\":%d}", chargeAmount);
        mockMvc.perform(patch("/point/{id}/charge", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chargeRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(initialPoint + chargeAmount));

        // 3. 충전 후 포인트 조회 - 충전 금액이 반영되었는지 확인
        MvcResult afterChargeResult = mockMvc.perform(get("/point/{id}", userId))
                .andExpect(status().isOk())
                .andReturn();

        String afterChargeResponse = afterChargeResult.getResponse().getContentAsString();
        long afterChargePoint = extractPointFromResponse(afterChargeResponse);
        assertThat(afterChargePoint).isEqualTo(initialPoint + chargeAmount);

        // 4. 포인트 사용
        String useRequest = String.format("{\"amount\":%d}", useAmount);
        mockMvc.perform(patch("/point/{id}/use", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(useRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(afterChargePoint - useAmount));

        // 5. 사용 후 포인트 조회 - 사용 금액이 반영되었는지 확인
        long expectedFinalPoint = initialPoint + chargeAmount - useAmount;
        mockMvc.perform(get("/point/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(expectedFinalPoint));

        // 6. 포인트 내역 조회 - 충전과 사용 내역이 모두 기록되었는지 확인
        mockMvc.perform(get("/point/{id}/histories", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value(userId))
                .andExpect(jsonPath("$[0].amount").value(chargeAmount))
                .andExpect(jsonPath("$[0].type").value("CHARGE"))
                .andExpect(jsonPath("$[1].userId").value(userId))
                .andExpect(jsonPath("$[1].amount").value(useAmount))
                .andExpect(jsonPath("$[1].type").value("USE"));
    }

    private long extractPointFromResponse(String jsonResponse) {
        // Simple JSON parsing to extract point value
        // Expected format: {"id":99,"point":1000,"updateMillis":123456}
        int pointIndex = jsonResponse.indexOf("\"point\":");
        if (pointIndex == -1) return 0L;

        int startIndex = pointIndex + 8; // length of "point":
        int endIndex = jsonResponse.indexOf(",", startIndex);
        if (endIndex == -1) {
            endIndex = jsonResponse.indexOf("}", startIndex);
        }

        String pointStr = jsonResponse.substring(startIndex, endIndex).trim();
        return Long.parseLong(pointStr);
    }

    @Test
    @DisplayName("대량 거래 부하 시나리오: 수백 건의 동시 거래에도 데이터 정합성이 유지된다")
    void massiveTransactions_maintainDataIntegrity() throws Exception {
        // given
        long userId = 999L; // 다른 테스트와 충돌하지 않도록 높은 ID 사용
        long initialAmount = 100000L; // 초기 10만원
        int chargeThreadCount = 50; // 충전 스레드 50개
        int useThreadCount = 50; // 사용 스레드 50개
        int totalThreadCount = chargeThreadCount + useThreadCount;
        long chargeAmount = 1000L; // 각 충전 1000원
        long useAmount = 500L; // 각 사용 500원

        // 초기 포인트 설정 (충전을 통해)
        String initialChargeRequest = String.format("{\"amount\":%d}", initialAmount);
        mockMvc.perform(patch("/point/{id}/charge", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initialChargeRequest))
                .andExpect(status().isOk());

        // when - 동시에 대량 거래 수행
        java.util.concurrent.ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(totalThreadCount);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(totalThreadCount);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger failureCount = new java.util.concurrent.atomic.AtomicInteger(0);

        // 충전 스레드 실행
        for (int i = 0; i < chargeThreadCount; i++) {
            executorService.execute(() -> {
                try {
                    String chargeRequest = String.format("{\"amount\":%d}", chargeAmount);
                    mockMvc.perform(patch("/point/{id}/charge", userId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(chargeRequest))
                            .andExpect(status().isOk());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // 사용 스레드 실행
        for (int i = 0; i < useThreadCount; i++) {
            executorService.execute(() -> {
                try {
                    String useRequest = String.format("{\"amount\":%d}", useAmount);
                    mockMvc.perform(patch("/point/{id}/use", userId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(useRequest))
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
        executorService.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);

        // then
        // 1. 최종 포인트 금액 검증
        long expectedFinalPoint = initialAmount + (chargeAmount * chargeThreadCount) - (useAmount * useThreadCount);
        // 100000 + (1000 * 50) - (500 * 50) = 100000 + 50000 - 25000 = 125000
        MvcResult finalResult = mockMvc.perform(get("/point/{id}", userId))
                .andExpect(status().isOk())
                .andReturn();

        String finalResponse = finalResult.getResponse().getContentAsString();
        long finalPoint = extractPointFromResponse(finalResponse);
        assertThat(finalPoint).isEqualTo(expectedFinalPoint);

        // 2. 거래 내역 검증 - 모든 거래가 기록되었는지 확인
        MvcResult historyResult = mockMvc.perform(get("/point/{id}/histories", userId))
                .andExpect(status().isOk())
                .andReturn();

        String historyResponse = historyResult.getResponse().getContentAsString();
        // 초기 충전 1건 + 대량 거래 100건 = 101건
        int expectedHistoryCount = 1 + totalThreadCount;

        // JSON 배열의 항목 개수 확인 (간단한 파싱)
        int historyCount = countJsonArrayElements(historyResponse);
        assertThat(historyCount).isEqualTo(expectedHistoryCount);

        // 3. 성공/실패 카운트 확인
        System.out.println("Success count: " + successCount.get());
        System.out.println("Failure count: " + failureCount.get());
        assertThat(successCount.get()).isEqualTo(totalThreadCount);
        assertThat(failureCount.get()).isEqualTo(0);
    }

    private int countJsonArrayElements(String jsonArrayResponse) {
        // Simple counting of objects in JSON array
        // Count the number of '},' or final '}]' patterns
        if (jsonArrayResponse == null || jsonArrayResponse.isEmpty() || jsonArrayResponse.equals("[]")) {
            return 0;
        }

        int count = 0;
        int index = 0;
        while ((index = jsonArrayResponse.indexOf("},{", index)) != -1) {
            count++;
            index += 2;
        }
        // Add 1 for the last element (which doesn't have },{ after it)
        if (jsonArrayResponse.contains("{")) {
            count++;
        }
        return count;
    }
}
