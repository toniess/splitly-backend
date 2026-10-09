package dev.toniess.splitly.expense;

import dev.toniess.splitly.IntegrationTest;
import dev.toniess.splitly.expense.dto.CreateExpenseRequest;
import dev.toniess.splitly.expense.dto.ExpenseResponse;
import dev.toniess.splitly.expense.dto.ParticipantInput;
import dev.toniess.splitly.expense.dto.UpdateExpenseRequest;
import dev.toniess.splitly.expense.entity.SplitType;
import dev.toniess.splitly.expense.exception.ExpenseVersionConflictException;
import dev.toniess.splitly.group.GroupMemberRepository;
import dev.toniess.splitly.group.GroupRepository;
import dev.toniess.splitly.group.entity.Group;
import dev.toniess.splitly.group.entity.GroupMember;
import dev.toniess.splitly.group.entity.GroupRole;
import dev.toniess.splitly.user.UserRepository;
import dev.toniess.splitly.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Две одновременные правки одной траты: пройти должна ровно одна.
 *
 * <p>Тест намеренно <b>не</b> помечен {@code @Transactional}. С аннотацией оба вызова
 * выполнялись бы в одной транзакции теста, никакой гонки бы не возникло,
 * и тест был бы зелёным, ничего не проверив.
 *
 * <p>Как следствие, записи реально коммитятся — поэтому за собой нужно убирать вручную.
 */
class ConcurrentUpdateIntegrationTest extends IntegrationTest {

    @Autowired ExpenseService expenseService;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired UserRepository userRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMemberRepository groupMemberRepository;

    private User anton;
    private Group group;
    private UUID expenseId;
    private ExecutorService pool;

    @BeforeEach
    void setUp() {
        anton = userRepository.save(new User(unique("anton"), "stub", "Антон"));
        group = groupRepository.save(new Group("Поездка", "UZS", anton, unique("C").substring(0, 8)));
        groupMemberRepository.save(new GroupMember(group, GroupRole.OWNER, anton));

        expenseId = expenseService.create(anton.getId(), group.getId(),
                new CreateExpenseRequest("Такси", new BigDecimal("300.00"), anton.getId(),
                        Instant.parse("2026-10-02T10:00:00Z"), SplitType.EQUAL,
                        List.of(new ParticipantInput(anton.getId(), null)))).id();

        pool = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
        expenseRepository.deleteById(expenseId);
        groupMemberRepository.deleteAll(groupMemberRepository.findAllByGroupId(group.getId()));
        groupRepository.deleteById(group.getId());
        userRepository.deleteById(anton.getId());
    }

    @Test
    @DisplayName("Из двух одновременных правок проходит ровно одна, вторая получает конфликт")
    void onlyOneOfTwoConcurrentUpdatesSucceeds() throws Exception {
        List<Object> outcomes = runConcurrently(
                () -> expenseService.update(anton.getId(), expenseId, updateWith("500.00", 0)),
                () -> expenseService.update(anton.getId(), expenseId, updateWith("700.00", 0)));

        List<ExpenseResponse> succeeded = outcomes.stream()
                .filter(ExpenseResponse.class::isInstance)
                .map(ExpenseResponse.class::cast)
                .toList();
        List<Throwable> failed = outcomes.stream()
                .filter(Throwable.class::isInstance)
                .map(Throwable.class::cast)
                .toList();

        assertThat(succeeded).as("успешных правок").hasSize(1);
        assertThat(failed).as("конфликтов").hasSize(1);

        // исход детерминирован благодаря PESSIMISTIC_WRITE: проигравший ждёт на SELECT ... FOR UPDATE
        // и, дождавшись, читает уже новую версию — срабатывает явная проверка.
        // Без блокировки здесь мог прилететь и OptimisticLockingFailureException, и дедлок
        assertThat(failed.getFirst())
                .as("конфликт должен быть распознан, а не всплыть как внутренняя ошибка")
                .isInstanceOf(ExpenseVersionConflictException.class);
    }

    @Test
    @DisplayName("Победившая правка сохраняется целиком, проигравшая не оставляет следов")
    void losingUpdateLeavesNoTrace() throws Exception {
        runConcurrently(
                () -> expenseService.update(anton.getId(), expenseId, updateWith("500.00", 0)),
                () -> expenseService.update(anton.getId(), expenseId, updateWith("700.00", 0)));

        ExpenseResponse stored = expenseService.getById(anton.getId(), expenseId);

        assertThat(stored.amount())
                .as("сумма должна быть от одной из правок, а не смесь")
                .isIn(new BigDecimal("500.00"), new BigDecimal("700.00"));
        assertThat(stored.version()).as("версия выросла ровно на одну правку").isEqualTo(1);
        assertThat(stored.shares()).hasSize(1);
        assertThat(stored.shares().getFirst().amount())
                .as("доля обязана совпадать с итоговой суммой")
                .isEqualByComparingTo(stored.amount());
    }

    @Test
    @DisplayName("Правка с устаревшей версией отклоняется и после того, как гонка улеглась")
    void rejectsStaleVersionAfterRace() throws Exception {
        expenseService.update(anton.getId(), expenseId, updateWith("500.00", 0));

        assertThat(catchThrowable(() -> expenseService.update(anton.getId(), expenseId, updateWith("700.00", 0))))
                .isInstanceOf(ExpenseVersionConflictException.class);
    }

    // ---------- вспомогательное ----------

    /**
     * Запускает обе операции одновременно: потоки ждут на защёлке, пока не стартуют оба,
     * и только потом идут в базу. Без этого второй поток почти всегда опаздывал бы
     * настолько, что гонки не возникало.
     *
     * @return результаты в том же порядке — либо значение, либо брошенное исключение
     */
    private List<Object> runConcurrently(Callable<ExpenseResponse> first, Callable<ExpenseResponse> second)
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ExpenseResponse>> futures = List.of(
                pool.submit(awaiting(start, first)),
                pool.submit(awaiting(start, second)));

        start.countDown();

        List<Object> outcomes = new ArrayList<>();
        for (Future<ExpenseResponse> future : futures) {
            try {
                outcomes.add(future.get(10, TimeUnit.SECONDS));
            } catch (ExecutionException ex) {
                // настоящее исключение лежит внутри, сам ExecutionException ничего не говорит
                outcomes.add(ex.getCause());
            }
        }
        return outcomes;
    }

    private static Callable<ExpenseResponse> awaiting(CountDownLatch start, Callable<ExpenseResponse> action) {
        return () -> {
            start.await();
            return action.call();
        };
    }

    private UpdateExpenseRequest updateWith(String amount, int version) {
        return new UpdateExpenseRequest("Такси", new BigDecimal(amount), anton.getId(),
                Instant.parse("2026-10-02T10:00:00Z"), SplitType.EQUAL,
                List.of(new ParticipantInput(anton.getId(), null)), version);
    }

    private static Throwable catchThrowable(Runnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable thrown) {
            return thrown;
        }
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
