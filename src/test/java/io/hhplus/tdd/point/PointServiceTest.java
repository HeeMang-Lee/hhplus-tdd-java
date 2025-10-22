package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PointServiceTest {

    private PointService pointService;
    private UserPointTable userPointTable;

    @BeforeEach
    void setUp() {
        userPointTable = new UserPointTable();
        pointService = new PointService(userPointTable);
    }

    @Test
    @DisplayName("특정 유저의 포인트를 조회한다")
    void getUserPoint() {
        // given
        long userId = 1L;
        userPointTable.insertOrUpdate(userId, 1000L);

        // when
        UserPoint result = pointService.getUserPoint(userId);

        // then
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.point()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("특정 유저의 포인트를 충전한다")
    void chargePoint() {
        // given
        long userId = 1L;
        long chargeAmount = 500L;

        // when
        UserPoint result = pointService.chargePoint(userId, chargeAmount);

        // then
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.point()).isEqualTo(500L);
    }

    @Test
    @DisplayName("특정 유저의 포인트를 사용한다")
    void usePoint() {
        // given
        long userId = 1L;
        userPointTable.insertOrUpdate(userId, 1000L);

        // when
        UserPoint result = pointService.usePoint(userId, 300L);

        // then
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.point()).isEqualTo(700L);
    }
}
