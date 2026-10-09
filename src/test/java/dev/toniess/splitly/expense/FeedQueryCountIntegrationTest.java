package dev.toniess.splitly.expense;

import dev.toniess.splitly.IntegrationTest;
import dev.toniess.splitly.expense.dto.CreateExpenseRequest;
import dev.toniess.splitly.expense.dto.ExpenseFeedPage;
import dev.toniess.splitly.expense.dto.ParticipantInput;
import dev.toniess.splitly.expense.entity.SplitType;
import dev.toniess.splitly.group.GroupMemberRepository;
import dev.toniess.splitly.group.GroupRepository;
import dev.toniess.splitly.group.entity.Group;
import dev.toniess.splitly.group.entity.GroupMember;
import dev.toniess.splitly.group.entity.GroupRole;
import dev.toniess.splitly.user.UserRepository;
import dev.toniess.splitly.user.entity.User;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Лента трат не должна ходить в базу за каждой записью отдельно.
 *
 * <p>Проверка устроена так, чтобы N+1 нельзя было замаскировать:
 * у каждой траты <b>свой</b> плательщик. Будь плательщик общим, Hibernate сходил бы
 * за ним один раз и дальше брал из кеша первого уровня — счётчик показал бы +1
 * вместо +100, и тест прошёл бы при сломанном коде.
 */

@Transactional
class FeedQueryCountIntegrationTest extends IntegrationTest {

    private static final int EXPENSE_COUNT = 200;
    private static final int PAGE_SIZE = 100;

    /**
     * Потолок, а не точное значение: лента вправе сделать несколько запросов
     * (проверка членства, сама страница, дозагрузка связей). Ловим рост,
     * пропорциональный размеру страницы, — его ни при каком раскладе быть не должно.
     */
    private static final int QUERY_LIMIT = 10;

    @Autowired ExpenseService expenseService;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired UserRepository userRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMemberRepository groupMemberRepository;
    @Autowired EntityManager entityManager;

    private User anton;
    private Group group;

    @BeforeEach
    void fillFeed() {
        anton = userRepository.save(new User(unique("anton"), "stub", "Антон"));
        group = groupRepository.save(new Group("Поездка", "UZS", anton, unique("C").substring(0, 8)));
        groupMemberRepository.save(new GroupMember(group, GroupRole.OWNER, anton));

        // у каждой траты свой плательщик — иначе кеш первого уровня скроет N+1
        List<User> payers = new ArrayList<>();
        for (int i = 0; i < EXPENSE_COUNT; i++) {
            User payer = userRepository.save(new User(unique("payer" + i), "stub", "Плательщик " + i));
            groupMemberRepository.save(new GroupMember(group, GroupRole.MEMBER, payer));
            payers.add(payer);
        }
        groupMemberRepository.flush();

        for (int i = 0; i < EXPENSE_COUNT; i++) {
            User payer = payers.get(i);
            expenseService.create(anton.getId(), group.getId(),
                    new CreateExpenseRequest("Трата " + i, new BigDecimal("100.00"), payer.getId(),
                            Instant.parse("2026-10-02T10:00:00Z").plusSeconds(i), SplitType.EQUAL,
                            List.of(new ParticipantInput(payer.getId(), null))));
        }
        expenseRepository.flush();
    }

    @Test
    @DisplayName("Страница из 100 трат обходится единицами запросов, а не сотней")
    void feedDoesNotQueryPerExpense() {
        long queries = countQueriesOf(() -> expenseService.getFeed(anton.getId(), group.getId(), null, PAGE_SIZE));

        assertThat(queries)
                .as("запросов на страницу из %d трат", PAGE_SIZE)
                .isLessThan(QUERY_LIMIT);
    }

    @Test
    @DisplayName("Число запросов не зависит от размера страницы")
    void queryCountDoesNotGrowWithPageSize() {
        long forTen = countQueriesOf(() -> expenseService.getFeed(anton.getId(), group.getId(), null, 10));
        long forHundred = countQueriesOf(() -> expenseService.getFeed(anton.getId(), group.getId(), null, 100));

        assertThat(forHundred)
                .as("страница в десять раз больше не должна стоить дороже: %d против %d", forHundred, forTen)
                .isEqualTo(forTen);
    }

    @Test
    @DisplayName("Страница по курсору стоит столько же, сколько первая")
    void cursorPageCostsTheSame() {
        ExpenseFeedPage firstPage = expenseService.getFeed(anton.getId(), group.getId(), null, PAGE_SIZE);
        assertThat(firstPage.nextCursor()).as("должна быть вторая страница").isNotNull();

        long forFirst = countQueriesOf(() -> expenseService.getFeed(anton.getId(), group.getId(), null, PAGE_SIZE));
        long forSecond = countQueriesOf(() ->
                expenseService.getFeed(anton.getId(), group.getId(), firstPage.nextCursor(), PAGE_SIZE));

        assertThat(forSecond).isEqualTo(forFirst);
    }

    @Test
    @DisplayName("Лента действительно отдаёт запрошенное число трат")
    void returnsRequestedNumberOfExpenses() {
        // страховка: если бы лента возвращала пустоту, предыдущие тесты
        // «не ходила в базу» прошли бы совершенно честно
        ExpenseFeedPage page = expenseService.getFeed(anton.getId(), group.getId(), null, PAGE_SIZE);

        assertThat(page.items()).hasSize(PAGE_SIZE);
        assertThat(page.items()).extracting(item -> item.payerId()).doesNotHaveDuplicates();
    }

    // ---------- вспомогательное ----------

    /**
     * Считает обращения к базе за время работы операции.
     *
     * <p>{@code clear()} у контекста персистентности обязателен: сущности, созданные
     * в подготовке, лежат в памяти этой же транзакции, и без очистки лента взяла бы
     * их оттуда, не сходив в базу ни разу.
     */
    private long countQueriesOf(Runnable operation) {
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        operation.run();

        return statistics.getPrepareStatementCount();
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
