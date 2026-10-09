package com.laundryhub.controller.web;

import com.laundryhub.common.SecurityUtils;
import com.laundryhub.domain.enums.Role;
import com.laundryhub.dto.request.UpdateProfileRequest;
import com.laundryhub.dto.response.ProfileResponse;
import com.laundryhub.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** "My profile" page: always about the logged-in user, so there is no id in the URL to tamper with. */
@Controller
public class ProfileWebController {

    private final UserService userService;

    public ProfileWebController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public String view(Model model) {
        Long userId = SecurityUtils.currentUserId();
        model.addAttribute("user", userService.getById(userId));
        // only CUSTOMER accounts have a profile row (1:1 with customer_profiles)
        boolean hasProfile = SecurityUtils.currentRole() == Role.CUSTOMER;
        model.addAttribute("hasProfile", hasProfile);
        if (hasProfile) {
            ProfileResponse profile = userService.getProfile(userId);
            model.addAttribute("form", new UpdateProfileRequest(profile.fullName(), profile.phone(), profile.address()));
        }
        return "profile/view";
    }

    @PostMapping("/profile")
    public String update(@Valid @ModelAttribute("form") UpdateProfileRequest form, BindingResult result,
                         Model model, RedirectAttributes redirectAttributes) {
        Long userId = SecurityUtils.currentUserId();
        if (result.hasErrors()) {
            model.addAttribute("user", userService.getById(userId));
            model.addAttribute("hasProfile", true);
            return "profile/view";
        }
        userService.updateProfile(userId, form);
        redirectAttributes.addFlashAttribute("success", "บันทึกข้อมูลเรียบร้อยแล้ว");
        return "redirect:/profile";
    }
}
