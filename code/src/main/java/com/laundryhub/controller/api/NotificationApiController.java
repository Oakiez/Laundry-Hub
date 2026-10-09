package com.laundryhub.controller.api;

import com.laundryhub.common.PageableValidator;
import com.laundryhub.common.SecurityUtils;
import com.laundryhub.dto.response.NotificationResponse;
import com.laundryhub.mapper.NotificationMapper;
import com.laundryhub.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Notifications")
public class NotificationApiController {

    // เจ้าของหรือแอดมิน: #userId คือ path variable, principal.id คือ id ของผู้ล็อกอิน (AppUserDetails.getId())
    private static final String OWNER_OR_ADMIN = "hasRole('ADMIN') or #userId == authentication.principal.id";

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "createdAt", "read");

    private final NotificationService notificationService;
    private final NotificationMapper notificationMapper;

    public NotificationApiController(NotificationService notificationService,
                                     NotificationMapper notificationMapper) {
        this.notificationService = notificationService;
        this.notificationMapper = notificationMapper;
    }

    @GetMapping("/users/{userId}/notifications")
    @PreAuthorize(OWNER_OR_ADMIN)
    @Operation(summary = "List a user's notifications, optionally only unread (owner or admin, paged)")
    public PagedModel<NotificationResponse> list(
            @PathVariable Long userId,
            @RequestParam(required = false) Boolean unread,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        PageableValidator.requireSortableBy(pageable, SORTABLE_FIELDS);
        return new PagedModel<>(notificationService.findByUser(userId, unread, pageable)
                .map(notificationMapper::toResponse));
    }

    @PatchMapping("/notifications/{id}/read")
    @Operation(summary = "Mark a notification as read (owner only)")
    public NotificationResponse markRead(@PathVariable Long id) {
        return notificationMapper.toResponse(notificationService.markRead(id, SecurityUtils.currentUserId()));
    }
}
