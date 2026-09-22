package dev.toniess.splitly.group;

import dev.toniess.splitly.group.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {
    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse createGroup(
            @RequestHeader("X-User-Id") UUID actorId,
            @RequestBody @Valid CreateGroupRequest request
    ) {
        return groupService.createGroup(actorId, request.name(), request.currency());
    }

    @GetMapping
    public List<GroupSummary> getGroups(
            @RequestHeader("X-User-Id") UUID actorId
    ) {
        return groupService.findGroupsOfUser(actorId);
    }

    @GetMapping("{id}")
    public GroupResponse getGroup(
            @RequestHeader("X-User-Id") UUID actorId,
            @PathVariable UUID id
    ) {
        return groupService.getGroup(actorId, id);
    }

    @GetMapping("{id}/members")
    public List<GroupMemberResponse> members(
            @RequestHeader("X-User-Id") UUID actorId,
            @PathVariable UUID id
    ) {
        return groupService.getMembers(actorId, id);
    }

    @PostMapping("join")
    public GroupMemberResponse joinGroup(
            @RequestHeader("X-User-Id") UUID actorId,
            @RequestBody @Valid JoinGroupRequest request
    ) {
        return groupService.joinByInviteCode(actorId, request.inviteCode());
    }
}
