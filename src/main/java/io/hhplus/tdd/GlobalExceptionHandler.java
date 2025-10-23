package io.hhplus.tdd;

import io.hhplus.tdd.point.BalanceInsufficientException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        ErrorResponse response = new ErrorResponse("ValidationError", "잘못된 요청 값입니다.");
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(BalanceInsufficientException.class)
    public ResponseEntity<ErrorResponse> handleBalanceInsufficientException(BalanceInsufficientException e) {
        ErrorResponse response = new ErrorResponse("BalanceInsufficientException", e.getMessage());
        return ResponseEntity.badRequest().body(response);
    }
}
