package com.laundryhub.controller.api;

import com.laundryhub.common.SecurityUtils;
import com.laundryhub.domain.enums.Role;
import com.laundryhub.dto.request.BookSessionRequest;
import com.laundryhub.dto.response.PageResponse;
import com.laundryhub.dto.response.SessionResponse;
import com.laundryhub.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Sessions")
public class SessionApiController {
    private final SessionService sessions;

    public SessionApiController(SessionService sessions) {
        this.sessions = sessions;
    }

    // These values come from the authenticated principal, never from JSON/query parameters.
    private boolean isStaff() {
        return SecurityUtils.currentRole() == Role.STAFF || SecurityUtils.currentRole() == Role.ADMIN;
    }

    @PostMapping("/machines/{machineId}/sessions")
    @PreAuthorize("hasAnyRole('CUSTOMER','STAFF','ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Book a machine (owner or staff/admin)",
            description = "Duration 10–180 minutes; price and end time are calculated by the server. Overlap returns 409.")
    public SessionResponse book(@PathVariable Long machineId, @Valid @RequestBody BookSessionRequest request) {
        return sessions.book(machineId, SecurityUtils.currentUserId(), isStaff(), request);
    }

    @GetMapping("/machines/{machineId}/sessions")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(summary = "List a machine's sessions (staff/admin)")
    public PageResponse<SessionResponse> forMachine(@PathVariable Long machineId,
            @ParameterObject @PageableDefault(size = 10, sort = "startTime", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(sessions.findForMachine(machineId, isStaff(), pageable));
    }

    @GetMapping("/users/{userId}/sessions")
    @PreAuthorize("#userId == authentication.principal.id")
    @Operation(summary = "List own usage history, paged and sorted (owner)")
    public PageResponse<SessionResponse> forUser(@PathVariable Long userId,
            @ParameterObject @PageableDefault(size = 10, sort = "startTime", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(sessions.findForUser(userId, SecurityUtils.currentUserId(), isStaff(), pageable));
    }

    @GetMapping("/sessions/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','STAFF','ADMIN')")
    @Operation(summary = "Get a session (owner or staff/admin)")
    public SessionResponse findById(@PathVariable Long id) {
        return sessions.findById(id, SecurityUtils.currentUserId(), isStaff());
    }

    @PatchMapping("/sessions/{id}/start")
    @PreAuthorize("hasAnyRole('CUSTOMER','STAFF','ADMIN')")
    @Operation(summary = "Start a RESERVED session on an AVAILABLE machine (owner or staff/admin)")
    public SessionResponse start(@PathVariable Long id) {
        return sessions.start(id, SecurityUtils.currentUserId(), isStaff());
    }

    @PatchMapping("/sessions/{id}/finish")
    @PreAuthorize("hasAnyRole('CUSTOMER','STAFF','ADMIN')")
    @Operation(summary = "Finish an IN_USE session (owner or staff/admin)")
    public SessionResponse finish(@PathVariable Long id) {
        return sessions.finish(id, SecurityUtils.currentUserId(), isStaff());
    }

    @PatchMapping("/sessions/{id}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER','STAFF','ADMIN')")
    @Operation(summary = "Cancel a RESERVED session (owner or staff/admin)")
    public SessionResponse cancel(@PathVariable Long id) {
        return sessions.cancel(id, SecurityUtils.currentUserId(), isStaff());
    }
}
