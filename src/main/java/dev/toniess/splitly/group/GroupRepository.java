package dev.toniess.splitly.group;

import dev.toniess.splitly.group.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<Group, UUID> {

    @Query("SELECT m.group from GroupMember m WHERE m.user.id = :userId")
    List<Group> findAllByUserId(UUID userId);

    Optional<Group> findByInviteCode(String inviteCode);
}

