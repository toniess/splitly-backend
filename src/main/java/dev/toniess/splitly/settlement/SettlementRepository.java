package dev.toniess.splitly.settlement;

import dev.toniess.splitly.settlement.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SettlementRepository extends JpaRepository<Settlement, UUID> {
    @Query(value = """
                SELECT gm.user_id                    AS "userId",
                       u.display_name               AS "displayName",
                       COALESCE(SUM(p.delta), 0)    AS "balance"
                FROM group_members gm
                JOIN users u on u.id = gm.user_id
                LEFT JOIN (
                         SELECT e.payer_id AS user_id, SUM(e.amount) AS delta
                         FROM expenses e
                         WHERE e.group_id = :groupId AND e.deleted_at IS NULL
                         GROUP BY e.payer_id
                    UNION ALL
                         SELECT es.user_id, -SUM(es.amount)
                         FROM expenses e JOIN expense_shares es ON e.id = es.expense_id
                         WHERE e.group_id = :groupId AND e.deleted_at IS NULL
                         GROUP BY es.user_id
                    UNION ALL
                         SELECT from_user_id, amount
                         FROM settlements s
                         WHERE s.group_id = :groupId
                    UNION ALL
                        SELECT to_user_id, -amount
                        FROM settlements s
                        WHERE s.group_id = :groupId
                     ) p ON p.user_id = gm.user_id
                WHERE gm.group_id = :groupId
                GROUP BY gm.user_id, u.display_name
                ORDER BY "balance" DESC
""", nativeQuery = true)
    List<BalanceProjection> getBalances(UUID groupId);
}
