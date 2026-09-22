package dev.toniess.splitly.group;

import dev.toniess.splitly.group.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {
    boolean existsGroupMemberByUserIdAndGroupId(UUID userId, UUID groupId);

    @Query("SELECT m FROM GroupMember m JOIN FETCH m.user WHERE m.group.id = :groupId")
    List<GroupMember> findAllByGroupId(UUID groupId);
}

