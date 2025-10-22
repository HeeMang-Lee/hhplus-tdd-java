package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;

import java.util.List;

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
        return userPointTable.insertOrUpdate(userId, newPoint);
    }

    public UserPoint usePoint(long userId, long amount) {
        UserPoint currentPoint = userPointTable.selectById(userId);
        long newPoint = currentPoint.point() - amount;
        return userPointTable.insertOrUpdate(userId, newPoint);
    }

    public List<PointHistory> getPointHistory(long userId) {
        return null;
    }
}
