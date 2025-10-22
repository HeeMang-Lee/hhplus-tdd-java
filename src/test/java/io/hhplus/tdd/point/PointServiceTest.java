package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PointServiceTest {

    private PointService pointService;
    private UserPointTable userPointTable;
    private PointHistoryTable pointHistoryTable;

    @BeforeEach
    void setUp() {
        userPointTable = new UserPointTable();
        pointHistoryTable = new PointHistoryTable();
        pointService = new PointService(userPointTable, pointHistoryTable);
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

    @Test
    @DisplayName("특정 유저의 포인트 충전/사용 내역을 조회한다")
    void getPointHistory() {
        // given
        long userId = 1L;
        pointService.chargePoint(userId, 1000L);
        pointService.usePoint(userId, 300L);

        // when
        List<PointHistory> histories = pointService.getPointHistory(userId);

        // then
        assertThat(histories).hasSize(2);
        assertThat(histories.get(0).type()).isEqualTo(TransactionType.CHARGE);
        assertThat(histories.get(0).amount()).isEqualTo(1000L);
        assertThat(histories.get(1).type()).isEqualTo(TransactionType.USE);
        assertThat(histories.get(1).amount()).isEqualTo(300L);
    }
}
