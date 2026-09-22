package dev.toniess.splitly.group;


import dev.toniess.splitly.common.InviteCodeGenerator;
import dev.toniess.splitly.group.dto.GroupMemberResponse;
import dev.toniess.splitly.group.dto.GroupResponse;
import dev.toniess.splitly.group.dto.GroupSummary;
import dev.toniess.splitly.group.entity.Group;
import dev.toniess.splitly.group.entity.GroupMember;
import dev.toniess.splitly.group.entity.GroupRole;
import dev.toniess.splitly.group.exception.AlreadyMemberException;
import dev.toniess.splitly.group.exception.GroupNotFoundException;
import dev.toniess.splitly.user.UserRepository;
import dev.toniess.splitly.user.entity.User;
import dev.toniess.splitly.user.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Service
public class GroupService {
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final InviteCodeGenerator inviteCodeGenerator;

    public GroupService(UserRepository userRepository, GroupRepository groupRepository, GroupMemberRepository groupMemberRepository, InviteCodeGenerator inviteCodeGenerator) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.inviteCodeGenerator = inviteCodeGenerator;
    }

    @Transactional
    public GroupResponse createGroup(UUID actorId, String name, String currency) {
        final Optional<User> user = userRepository.findById(actorId);
        if (user.isEmpty()) throw new UserNotFoundException(actorId);

        final Group group = new Group(name, currency, user.get(), inviteCodeGenerator.generate());
        groupRepository.saveAndFlush(group);

        final GroupMember groupMember = new GroupMember(group, GroupRole.OWNER, user.get());
        groupMemberRepository.saveAndFlush(groupMember);

        return GroupResponse.from(group);
    }

    @Transactional(readOnly = true)
    public List<GroupSummary> findGroupsOfUser(UUID actorId) {
        return groupRepository.findAllByUserId(actorId)
                .stream()
                .map(GroupSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroup(UUID actorId, UUID groupId) {
        final boolean userIsInGroup = groupMemberRepository.existsGroupMemberByUserIdAndGroupId(actorId, groupId);
        if (!userIsInGroup) throw GroupNotFoundException.byId(groupId);
        final Optional<Group> group = groupRepository.findById(groupId);
        if (group.isEmpty()) throw GroupNotFoundException.byId(groupId);

        return GroupResponse.from(group.get());
    }

    @Transactional(readOnly = true)
    public List<GroupMemberResponse> getMembers(UUID actorId, UUID groupId) {
        final boolean userIsInGroup = groupMemberRepository.existsGroupMemberByUserIdAndGroupId(actorId, groupId);
        if (!userIsInGroup) throw GroupNotFoundException.byId(groupId);
        return groupMemberRepository.findAllByGroupId(groupId)
                .stream()
                .map(GroupMemberResponse::from)
                .toList();
    }

    @Transactional
    public GroupMemberResponse joinByInviteCode(UUID actorId, String inviteCode) {
        final Optional<Group> group = groupRepository.findByInviteCode(inviteCode);
        if (group.isEmpty()) throw GroupNotFoundException.byInviteCode(inviteCode);
        final boolean userIsInGroup = groupMemberRepository.existsGroupMemberByUserIdAndGroupId(actorId, group.get().getId());
        if (userIsInGroup) throw new AlreadyMemberException(actorId, group.get().getId());

        final Optional<User> user = userRepository.findById(actorId);
        if (user.isEmpty()) throw new UserNotFoundException(actorId);
        final GroupMember newMember = new GroupMember(group.get(), GroupRole.MEMBER, user.get());
        groupMemberRepository.saveAndFlush(newMember);
        return GroupMemberResponse.from(newMember);
    }

}
