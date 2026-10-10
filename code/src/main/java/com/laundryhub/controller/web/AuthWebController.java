package com.laundryhub.controller.web;

import com.laundryhub.dto.request.RegisterRequest;
import com.laundryhub.exception.DuplicateResourceException;
import com.laundryhub.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthWebController {

    private final AuthService authService;

    public AuthWebController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("form", new RegisterRequest(null, null, null, null, null, null));
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterRequest form, BindingResult result,
                           @RequestParam(defaultValue = "") String confirmPassword,
                           Model model, RedirectAttributes redirectAttributes) {
        // the second password box exists only on the web form (typo guard), the REST API does not need it
        boolean mismatch = form.password() != null && !form.password().equals(confirmPassword);
        if (mismatch) {
            model.addAttribute("confirmError", "รหัสผ่านทั้งสองช่องไม่ตรงกัน");
        }
        if (result.hasErrors() || mismatch) {
            return "auth/register";
        }
        try {
            authService.register(form);
        } catch (DuplicateResourceException e) {
            model.addAttribute("error", e.getMessage());
            return "auth/register";
        }
        redirectAttributes.addFlashAttribute("success", "สมัครสมาชิกสำเร็จ กรุณาเข้าสู่ระบบ");
        return "redirect:/login";
    }
}
