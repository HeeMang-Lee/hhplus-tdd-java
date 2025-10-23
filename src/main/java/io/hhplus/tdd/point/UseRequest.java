package io.hhplus.tdd.point;

import io.hhplus.tdd.point.validation.MultipleOf;
import jakarta.validation.constraints.Positive;

public record UseRequest(
        @Positive(message = "사용 금액은 0보다 커야 합니다")
        @MultipleOf(value = 100, message = "사용 금액은 100원 단위여야 합니다")
        long amount
) {
}
