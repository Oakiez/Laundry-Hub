package com.laundryhub.controller.web;

import com.laundryhub.common.SecurityUtils;
import com.laundryhub.dto.response.NotificationResponse;
import com.laundryhub.exception.ResourceNotFoundException;
import com.laundryhub.mapper.NotificationMapper;
import com.laundryhub.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** "My notifications" page: always about the logged-in user, so there is no user id in the URL to tamper with. */
@Controller
@RequestMapping("/notifications")
public class NotificationWebController {

    private static final int PAGE_SIZE = 10;

    private final NotificationService notificationService;
    private final NotificationMapper notificationMapper;

    public NotificationWebController(NotificationService notificationService,
                                     NotificationMapper notificationMapper) {
        this.notificationService = notificationService;
        this.notificationMapper = notificationMapper;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "false") boolean unread,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<NotificationResponse> notifications = notificationService
                .findByUser(SecurityUtils.currentUserId(), unread ? Boolean.TRUE : null, pageable)
                .map(notificationMapper::toResponse);
        model.addAttribute("notifications", notifications);
        model.addAttribute("unread", unread);
        return "notifications/list";
    }

    @PostMapping("/{id:\\d+}/read")
    public String markRead(@PathVariable Long id,
                           @RequestParam(defaultValue = "false") boolean unread,
                           RedirectAttributes redirectAttributes) {
        try {
            notificationService.markRead(id, SecurityUtils.currentUserId());
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "คุณอ่านได้เฉพาะแจ้งเตือนของตัวเองเท่านั้น");
        }
        return unread ? "redirect:/notifications?unread=true" : "redirect:/notifications";
    }
}
