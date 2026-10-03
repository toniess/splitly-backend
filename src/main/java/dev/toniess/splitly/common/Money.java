package dev.toniess.splitly.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Правила обращения с деньгами в проекте.
 *
 * <p>В базе суммы хранятся как {@code NUMERIC(19,4)} — четыре знака нужны как запас
 * на промежуточные вычисления. Бизнес-правила и контракт API оперируют двумя знаками,
 * поэтому на границе наружу значение приводится к {@link #SCALE}.
 *
 * <p>Сравнивать суммы можно только через {@code compareTo}: {@code equals} у
 * {@link BigDecimal} учитывает scale, и {@code 10.0} не равно {@code 10.00}.
 */
public final class Money {

    /** Знаков после запятой в представлении суммы. */
    public static final int SCALE = 2;

    /** Режим округления для всех денежных операций. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private Money() {
    }

    /**
     * Приводит сумму к каноническому виду: ровно {@link #SCALE} знаков после запятой.
     *
     * @param amount сумма; {@code null} возвращается как есть
     */
    public static BigDecimal normalize(BigDecimal amount) {
        return amount == null ? null : amount.setScale(SCALE, ROUNDING);
    }
}
