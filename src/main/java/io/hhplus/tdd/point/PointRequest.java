package io.hhplus.tdd.point;

import jakarta.validation.constraints.Positive;

public record PointRequest(
        @Positive(message = "금액은 0보다 커야 합니다")
        long amount
) {
}
