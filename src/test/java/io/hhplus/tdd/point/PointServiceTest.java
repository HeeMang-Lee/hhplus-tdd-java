package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    private UserPointTable userPointTable;

    @Mock
    private PointHistoryTable pointHistoryTable;

    @InjectMocks
    private PointService pointService;

    private static final long FIXED_TIME = 1234567890L;

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
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.point()).isEqualTo(1000L);
        assertThat(result.updateMillis()).isEqualTo(FIXED_TIME);
        verify(userPointTable, times(1)).selectById(userId);
    }

    @Test
    @DisplayName("포인트를 충전한다")
    void chargePoint() {
        // given
        long userId = 1L;
        long currentAmount = 1000L;
        long chargeAmount = 500L;
        long expectedAmount = 1500L;

        UserPoint currentPoint = new UserPoint(userId, currentAmount, FIXED_TIME);
        UserPoint updatedPoint = new UserPoint(userId, expectedAmount, FIXED_TIME);

        when(userPointTable.selectById(userId)).thenReturn(currentPoint);
        when(userPointTable.insertOrUpdate(userId, expectedAmount)).thenReturn(updatedPoint);

        // when
        UserPoint result = pointService.chargePoint(userId, chargeAmount);

        // then
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.point()).isEqualTo(expectedAmount);
        assertThat(result.updateMillis()).isEqualTo(FIXED_TIME);
        verify(userPointTable, times(1)).selectById(userId);
        verify(userPointTable, times(1)).insertOrUpdate(userId, expectedAmount);
        verify(pointHistoryTable, times(1)).insert(eq(userId), eq(chargeAmount), eq(TransactionType.CHARGE), eq(FIXED_TIME));
    }

    @Test
    @DisplayName("포인트를 사용한다")
    void usePoint() {
        // given
        long userId = 1L;
        long currentAmount = 1000L;
        long useAmount = 300L;
        long expectedAmount = 700L;

        UserPoint currentPoint = new UserPoint(userId, currentAmount, FIXED_TIME);
        UserPoint updatedPoint = new UserPoint(userId, expectedAmount, FIXED_TIME);

        when(userPointTable.selectById(userId)).thenReturn(currentPoint);
        when(userPointTable.insertOrUpdate(userId, expectedAmount)).thenReturn(updatedPoint);

        // when
        UserPoint result = pointService.usePoint(userId, useAmount);

        // then
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.point()).isEqualTo(expectedAmount);
        assertThat(result.updateMillis()).isEqualTo(FIXED_TIME);
        verify(userPointTable, times(1)).selectById(userId);
        verify(userPointTable, times(1)).insertOrUpdate(userId, expectedAmount);
        verify(pointHistoryTable, times(1)).insert(eq(userId), eq(useAmount), eq(TransactionType.USE), eq(FIXED_TIME));
    }

    @Test
    @DisplayName("포인트 내역을 조회한다")
    void getPointHistory() {
        // given
        long userId = 1L;
        List<PointHistory> expectedHistories = List.of(
                new PointHistory(1L, userId, 1000L, TransactionType.CHARGE, FIXED_TIME),
                new PointHistory(2L, userId, 300L, TransactionType.USE, FIXED_TIME)
        );

        when(pointHistoryTable.selectAllByUserId(userId)).thenReturn(expectedHistories);

        // when
        List<PointHistory> result = pointService.getPointHistory(userId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).type()).isEqualTo(TransactionType.CHARGE);
        assertThat(result.get(0).amount()).isEqualTo(1000L);
        assertThat(result.get(1).type()).isEqualTo(TransactionType.USE);
        assertThat(result.get(1).amount()).isEqualTo(300L);
        verify(pointHistoryTable, times(1)).selectAllByUserId(userId);
    }

    @Test
    @DisplayName("사용 금액이 보유 포인트보다 크면 예외가 발생한다")
    void usePoint_insufficientBalance() {
        // given
        long userId = 1L;
        long currentAmount = 500L;
        long useAmount = 1000L;

        UserPoint currentPoint = new UserPoint(userId, currentAmount, FIXED_TIME);
        when(userPointTable.selectById(userId)).thenReturn(currentPoint);

        // when & then
        assertThatThrownBy(() -> pointService.usePoint(userId, useAmount))
                .isInstanceOf(BalanceInsufficientException.class)
                .hasMessage("잔고가 부족합니다.");
    }
}
