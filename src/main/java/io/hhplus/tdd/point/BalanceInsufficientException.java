package io.hhplus.tdd.point;

public class BalanceInsufficientException extends RuntimeException {
    public BalanceInsufficientException(String message) {
        super(message);
    }
}
