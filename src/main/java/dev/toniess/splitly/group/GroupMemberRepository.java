package dev.toniess.splitly.group;

import dev.toniess.splitly.group.entity.Group;
import dev.toniess.splitly.group.entity.GroupMember;
import dev.toniess.splitly.group.entity.GroupRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {
    boolean existsGroupMemberByUserIdAndGroupId(UUID userId, UUID groupId);

    @Query("SELECT m FROM GroupMember m JOIN FETCH m.user WHERE m.group.id = :groupId")
    List<GroupMember> findAllByGroupId(UUID groupId);

    @Query("SELECT m.user.id FROM GroupMember m WHERE m.group.id = :groupId")
    Set<UUID> findUserIdsByGroupId(UUID groupId);

    boolean existsByUserIdAndGroupIdAndRole(UUID userId, UUID groupId, GroupRole role);

    UUID group(Group group);
}

