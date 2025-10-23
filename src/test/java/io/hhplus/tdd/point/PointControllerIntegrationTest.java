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
}
