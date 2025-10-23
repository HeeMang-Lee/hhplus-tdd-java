package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    public UserPoint getUserPoint(long userId) {
        return userPointTable.selectById(userId);
    }

    public UserPoint chargePoint(long userId, long amount) {
        UserPoint currentPoint = userPointTable.selectById(userId);
        long newPoint = currentPoint.point() + amount;
        UserPoint updatedPoint = userPointTable.insertOrUpdate(userId, newPoint);
        pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, updatedPoint.updateMillis());
        return updatedPoint;
    }

    public UserPoint usePoint(long userId, long amount) {
        UserPoint currentPoint = userPointTable.selectById(userId);

        if (currentPoint.point() < amount) {
            throw new BalanceInsufficientException("잔고가 부족합니다.");
        }

        long newPoint = currentPoint.point() - amount;
        UserPoint updatedPoint = userPointTable.insertOrUpdate(userId, newPoint);
        pointHistoryTable.insert(userId, amount, TransactionType.USE, updatedPoint.updateMillis());
        return updatedPoint;
    }

    public List<PointHistory> getPointHistory(long userId) {
        return pointHistoryTable.selectAllByUserId(userId);
    }
}
