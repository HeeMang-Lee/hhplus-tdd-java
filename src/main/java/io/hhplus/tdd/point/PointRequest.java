package io.hhplus.tdd.point;

import io.hhplus.tdd.point.validation.MultipleOf;
import jakarta.validation.constraints.Min;

public record PointRequest(
        @Min(value = 1000, message = "충전 금액은 최소 1000원 이상이어야 합니다")
        @MultipleOf(value = 1000, message = "충전 금액은 1000원 단위여야 합니다")
        long amount
) {
}
