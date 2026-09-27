package dev.toniess.splitly.expense.entity;

import dev.toniess.splitly.group.entity.Group;
import dev.toniess.splitly.user.entity.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "expenses")
public class Expense {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id")
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id")
    private User payer;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 3)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    private String description;

    private Instant spentAt;

    @Enumerated(value = EnumType.STRING)
    private SplitType splitType;

    @Version
    private int version;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    private Instant deletedAt;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExpenseShare> shares = new ArrayList<>();

    protected Expense() {}
}
