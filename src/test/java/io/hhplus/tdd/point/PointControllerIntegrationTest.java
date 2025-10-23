package io.hhplus.tdd.point;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
}
