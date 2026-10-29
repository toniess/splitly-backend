package dev.toniess.splitly.expense.split;

import dev.toniess.splitly.expense.entity.SplitType;
import dev.toniess.splitly.expense.exception.InvalidSplitException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


@Component
public class ShareSplitStrategy implements SplitStrategy {

    @Override
    public SplitType type() {
        return SplitType.SHARES;
    }

    /**
     * Делит сумму пропорционально целым весам.
     *
     * <p>База каждой доли — {@code сумма × вес / сумма весов} в копейках, округлённая вниз.
     * Оставшиеся копейки раздаются по одной участникам с ненулевым весом в порядке userId:
     * вес 0 означает «не участвует в оплате», и копейка остатка ему не положена.
     *
     * <p>Считается в {@link BigInteger}: сумма до 10<sup>15</sup> и вес до 10<sup>15</sup>
     * (столько пропускает валидация запроса) в произведении не помещаются в {@code long}.
     */
    @Override
    public List<ShareAmount> split(BigDecimal amount, List<SplitInput> participants) {

        // Эти два случая отсекает валидация запроса — сюда они доходят только по ошибке в коде
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount should be greater than 0, got " + amount);
        }
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("Participants should not be empty");
        }

        // А это ввод пользователя: ответ 422 с объяснением
        if (participants.stream().anyMatch(p -> p.value() == null)) {
            throw new InvalidSplitException("При делении по долям у каждого участника должна быть доля");
        }
        if (participants.stream().anyMatch(p -> p.value().signum() < 0)) {
            throw new InvalidSplitException("Доля не может быть отрицательной");
        }
        // 2 и 2.00 — одно и то же целое; 1.5 — нет
        if (participants.stream().anyMatch(p -> p.value().stripTrailingZeros().scale() > 0)) {
            throw new InvalidSplitException("Доли должны быть целыми числами: 1, 2, 3…");
        }
        if (participants.stream().allMatch(p -> p.value().signum() == 0)) {
            throw new InvalidSplitException("Хотя бы у одного участника доля должна быть больше нуля");
        }

        final BigInteger totalCents = amount.movePointRight(2).toBigIntegerExact();
        final List<SplitInput> sorted = participants.stream()
                .sorted(Comparator.comparing(SplitInput::userId))
                .toList();
        final BigInteger totalWeight = sorted.stream()
                .map(p -> p.value().toBigIntegerExact())
                .reduce(BigInteger.ZERO, BigInteger::add);

        final List<BigInteger> base = sorted.stream()
                .map(p -> totalCents.multiply(p.value().toBigIntegerExact()).divide(totalWeight))
                .toList();

        // Каждая база теряет меньше копейки, поэтому остаток меньше числа участников с весом
        long remainder = totalCents.subtract(base.stream().reduce(BigInteger.ZERO, BigInteger::add)).longValueExact();

        final List<ShareAmount> splitted = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            BigInteger cents = base.get(i);
            if (remainder > 0 && sorted.get(i).value().signum() > 0) {
                cents = cents.add(BigInteger.ONE);
                remainder--;
            }
            splitted.add(new ShareAmount(sorted.get(i).userId(), new BigDecimal(cents, 2)));
        }

        final BigDecimal sum = splitted.stream().map(ShareAmount::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.compareTo(sum) != 0) {
            throw new IllegalStateException(
                    "Split produced " + sum + " instead of " + amount
                            + " for weights " + participants.stream().map(SplitInput::value).toList());
        }

        return splitted;
    }
}
