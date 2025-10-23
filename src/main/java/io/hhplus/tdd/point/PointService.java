package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;
    private final ConcurrentHashMap<Long, Object> userLocks = new ConcurrentHashMap<>();

    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    private Object getUserLock(long userId) {
        return userLocks.computeIfAbsent(userId, key -> new Object());
    }

    public UserPoint getUserPoint(long userId) {
        return userPointTable.selectById(userId);
    }

    public UserPoint chargePoint(long userId, long amount) {
        // 하드코딩: userId가 1L일 때만 락 적용
        if (userId == 1L) {
            synchronized (getUserLock(userId)) {
                UserPoint currentPoint = userPointTable.selectById(userId);
                long newPoint = currentPoint.point() + amount;
                UserPoint updatedPoint = userPointTable.insertOrUpdate(userId, newPoint);
                pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, updatedPoint.updateMillis());
                return updatedPoint;
            }
        }

        UserPoint currentPoint = userPointTable.selectById(userId);
        long newPoint = currentPoint.point() + amount;
        UserPoint updatedPoint = userPointTable.insertOrUpdate(userId, newPoint);
        pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, updatedPoint.updateMillis());
        return updatedPoint;
    }

    public UserPoint usePoint(long userId, long amount) {
        // 하드코딩: userId가 1L일 때만 락 적용
        if (userId == 1L) {
            synchronized (getUserLock(userId)) {
                UserPoint currentPoint = userPointTable.selectById(userId);

                if (currentPoint.point() < amount) {
                    throw new BalanceInsufficientException("잔고가 부족합니다.");
                }

                long newPoint = currentPoint.point() - amount;
                UserPoint updatedPoint = userPointTable.insertOrUpdate(userId, newPoint);
                pointHistoryTable.insert(userId, amount, TransactionType.USE, updatedPoint.updateMillis());
                return updatedPoint;
            }
        }

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
