package dev.toniess.splitly.group.entity;

import dev.toniess.splitly.user.entity.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "group_members")
public class GroupMember {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id")
    private Group group;

    @Enumerated(EnumType.STRING)
    private GroupRole role;

    @CreationTimestamp
    private Instant joinedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;


    public GroupMember(Group group, GroupRole role, User user) {
        this.group = group;
        this.role = role;
        this.user = user;
    }

    protected GroupMember() { }

    public UUID getId() { return id; }

    public Group getGroup() { return group; }

    public GroupRole getRole() { return role; }

    public Instant getJoinedAt() { return joinedAt; }

    public User getUser() { return user; }
}