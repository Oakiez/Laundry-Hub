package com.laundryhub.controller.web;

import com.laundryhub.common.SecurityUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeWebController {

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("role", SecurityUtils.currentRole());
        return "home";
    }
}
