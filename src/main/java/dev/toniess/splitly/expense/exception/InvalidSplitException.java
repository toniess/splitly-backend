package dev.toniess.splitly.expense.exception;

import dev.toniess.splitly.common.exception.UnprocessableException;

/**
 * Доли участников не подходят выбранному способу деления: пустая доля,
 * дробный вес, все веса нулевые. Это ошибка клиента — ответ 422 с объяснением,
 * а не 500 из глубины стратегии.
 */
public class InvalidSplitException extends UnprocessableException {
    public InvalidSplitException(String message) {
        super(message);
    }
}
