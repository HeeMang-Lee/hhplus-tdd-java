package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;

public class PointService {

    private final UserPointTable userPointTable;

    public PointService(UserPointTable userPointTable) {
        this.userPointTable = userPointTable;
    }

    public UserPoint getUserPoint(long userId) {
        return userPointTable.selectById(userId);
    }

    public UserPoint chargePoint(long userId, long amount) {
        UserPoint currentPoint = userPointTable.selectById(userId);
        long newPoint = currentPoint.point() + amount;
        return userPointTable.insertOrUpdate(userId, newPoint);
    }
}
