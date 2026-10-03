package dev.toniess.splitly.expense;

import dev.toniess.splitly.expense.entity.Expense;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface ExpenseRepository extends JpaRepository<Expense, UUID> {
    @EntityGraph(attributePaths = {"shares", "shares.user"})
    Optional<Expense> findByIdAndDeletedAtIsNull(UUID id);

    @Query(value = """
        SELECT * FROM expenses
        WHERE group_id = :groupId AND deleted_at IS NULL
        ORDER BY spent_at DESC, id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Expense> findFirstPage(UUID groupId, int limit);

    @Query(value = """
        SELECT * FROM expenses
        WHERE group_id = :groupId AND deleted_at IS NULL AND (spent_at, id) < (:cursorSpentAt, :cursorId)
        ORDER BY spent_at DESC, id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Expense> findPage(UUID groupId, int limit, Instant cursorSpentAt, UUID cursorId);


}
