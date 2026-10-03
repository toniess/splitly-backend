package dev.toniess.splitly.expense.entity;

import dev.toniess.splitly.expense.dto.ParticipantInput;
import dev.toniess.splitly.group.entity.Group;
import dev.toniess.splitly.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

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
    private final List<ExpenseShare> shares = new ArrayList<>();

    protected Expense() {}

    public Expense(
        String description,
        BigDecimal amount,
        User payer,
        Group group,
        String currency,
        Instant spentAt,
        SplitType splitType,
        List<ExpenseShare> participants
    ) {
        this.description = description;
        this.amount = amount;
        this.payer = payer;
        this.group = group;
        this.currency = currency;
        this.spentAt = spentAt;
        this.splitType = splitType;
        participants.forEach(this::addShare);
    }

    public void update(
            String description,
            BigDecimal amount,
            User payer,
            Instant spentAt,
            SplitType splitType,
            List<ExpenseShare> participants
    ) {
        this.description = description;
        this.amount = amount;
        this.payer = payer;
        this.spentAt = spentAt;
        this.splitType = splitType;
        shares.clear();
        participants.forEach(this::addShare);
    }

    public void addShare(ExpenseShare share) {
        shares.add(share);
        share.setExpense(this);
    }

    public UUID getId() {
        return id;
    }

    public Group getGroup() {
        return group;
    }

    public User getPayer() {
        return payer;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public Instant getSpentAt() {
        return spentAt;
    }

    public SplitType getSplitType() {
        return splitType;
    }

    public int getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ExpenseShare> getShares() {
        return new ArrayList<>(shares);
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
