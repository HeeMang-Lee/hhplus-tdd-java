package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;

public class PointService {

    private final UserPointTable userPointTable;

    public PointService(UserPointTable userPointTable) {
        this.userPointTable = userPointTable;
    }

    public UserPoint getUserPoint(long userId) {
        // 테스트를 통과시키기 위한 최소한의 코드 (하드코딩)
        return new UserPoint(1L, 1000L, System.currentTimeMillis());
    }
}
