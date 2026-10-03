package dev.toniess.splitly.expense.exception;

import dev.toniess.splitly.common.exception.UnprocessableException;

public class SharesDoNotMatchAmountException extends UnprocessableException {
    public SharesDoNotMatchAmountException(String message) {
        super(message);
    }
}
