package io.hhplus.tdd.point;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PointController.class)
class PointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PointService pointService;

    private static final long FIXED_TIME = 1234567890L;

    @Test
    @DisplayName("특정 유저의 포인트를 조회한다")
    void getUserPoint() throws Exception {
        // given
        long userId = 1L;
        UserPoint userPoint = new UserPoint(userId, 1000L, FIXED_TIME);
        when(pointService.getUserPoint(userId)).thenReturn(userPoint);

        // when & then
        mockMvc.perform(get("/point/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(1000L));
    }

    @Test
    @DisplayName("포인트를 충전한다")
    void chargePoint() throws Exception {
        // given
        long userId = 1L;
        long amount = 500L;
        UserPoint chargedPoint = new UserPoint(userId, amount, FIXED_TIME);
        when(pointService.chargePoint(userId, amount)).thenReturn(chargedPoint);

        // when & then
        mockMvc.perform(patch("/point/{id}/charge", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + amount + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(amount));
    }

    @Test
    @DisplayName("포인트를 사용한다")
    void usePoint() throws Exception {
        // given
        long userId = 1L;
        long useAmount = 300L;
        UserPoint usedPoint = new UserPoint(userId, 700L, FIXED_TIME);
        when(pointService.usePoint(userId, useAmount)).thenReturn(usedPoint);

        // when & then
        mockMvc.perform(patch("/point/{id}/use", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + useAmount + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(700L));
    }

    @Test
    @DisplayName("포인트 내역을 조회한다")
    void getPointHistory() throws Exception {
        // given
        long userId = 1L;
        List<PointHistory> histories = List.of(
                new PointHistory(1L, userId, 1000L, TransactionType.CHARGE, FIXED_TIME),
                new PointHistory(2L, userId, 300L, TransactionType.USE, FIXED_TIME)
        );
        when(pointService.getPointHistory(userId)).thenReturn(histories);

        // when & then
        mockMvc.perform(get("/point/{id}/histories", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("CHARGE"))
                .andExpect(jsonPath("$[0].amount").value(1000))
                .andExpect(jsonPath("$[1].type").value("USE"))
                .andExpect(jsonPath("$[1].amount").value(300));
    }

    @Test
    @DisplayName("충전 금액이 0이면 실패한다")
    void chargePoint_invalidAmount_zero() throws Exception {
        // given
        long userId = 1L;
        long invalidAmount = 0L;

        // when & then
        mockMvc.perform(patch("/point/{id}/charge", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + invalidAmount + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("충전 금액이 음수면 실패한다")
    void chargePoint_invalidAmount_negative() throws Exception {
        // given
        long userId = 1L;
        long invalidAmount = -100L;

        // when & then
        mockMvc.perform(patch("/point/{id}/charge", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + invalidAmount + "}"))
                .andExpect(status().isBadRequest());
    }
}
