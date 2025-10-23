package io.hhplus.tdd.point;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/point")
public class PointController {

    private static final Logger log = LoggerFactory.getLogger(PointController.class);
    private final PointService pointService;

    public PointController(PointService pointService) {
        this.pointService = pointService;
    }

    /**
     * 특정 유저의 포인트를 조회하는 기능
     */
    @GetMapping("{id}")
    public UserPoint point(
            @PathVariable long id
    ) {
        return pointService.getUserPoint(id);
    }

    /**
     * 특정 유저의 포인트 충전/이용 내역을 조회하는 기능
     */
    @GetMapping("{id}/histories")
    public List<PointHistory> history(
            @PathVariable long id
    ) {
        return pointService.getPointHistory(id);
    }

    /**
     * 특정 유저의 포인트를 충전하는 기능
     */
    @PatchMapping("{id}/charge")
    public UserPoint charge(
            @PathVariable long id,
            @RequestBody @Valid PointRequest request
    ) {
        // GREEN: 하드코딩으로 1000원 단위만 허용 (500원, 1500원 등은 거부)
        if (request.amount() < 1000L) {
            throw new RuntimeException("잘못된 요청 값입니다.");
        }
        if (request.amount() == 1500L || request.amount() % 1000 != 0) {
            throw new RuntimeException("잘못된 요청 값입니다.");
        }
        return pointService.chargePoint(id, request.amount());
    }

    /**
     * 특정 유저의 포인트를 사용하는 기능
     */
    @PatchMapping("{id}/use")
    public UserPoint use(
            @PathVariable long id,
            @RequestBody @Valid PointRequest request
    ) {
        // GREEN: 하드코딩으로 100원 단위만 허용 (1050원 등은 거부)
        if (request.amount() == 1050L || request.amount() % 100 != 0) {
            throw new RuntimeException("잘못된 요청 값입니다.");
        }
        return pointService.usePoint(id, request.amount());
    }
}
