package com.laundryhub.controller.web;

import com.laundryhub.dto.request.BranchRequest;
import com.laundryhub.dto.response.BranchResponse;
import com.laundryhub.exception.ResourceNotFoundException;
import com.laundryhub.service.BranchService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin pages for branches. The /admin/** URL rule in SecurityConfig already limits this to ADMIN. */
@Controller
@RequestMapping("/admin/branches")
public class BranchWebController {

    private static final String LIST_REDIRECT = "redirect:/admin/branches";
    private static final String FORM_VIEW = "branches/form";

    private final BranchService branchService;

    public BranchWebController(BranchService branchService) {
        this.branchService = branchService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("branches", branchService.findAll());
        return "branches/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new BranchRequest(null, null, null));
        model.addAttribute("branchId", null);
        return FORM_VIEW;
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            BranchResponse branch = branchService.findById(id);
            model.addAttribute("form", new BranchRequest(branch.name(), branch.address(), branch.phone()));
            model.addAttribute("branchId", id);
            return FORM_VIEW;
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return LIST_REDIRECT;
        }
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") BranchRequest form, BindingResult result,
                         Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("branchId", null);
            return FORM_VIEW;
        }
        branchService.create(form);
        redirectAttributes.addFlashAttribute("success", "เพิ่มสาขาเรียบร้อยแล้ว");
        return LIST_REDIRECT;
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") BranchRequest form,
                         BindingResult result, Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("branchId", id);
            return FORM_VIEW;
        }
        try {
            branchService.update(id, form);
            redirectAttributes.addFlashAttribute("success", "แก้ไขสาขาเรียบร้อยแล้ว");
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return LIST_REDIRECT;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            branchService.delete(id);
            redirectAttributes.addFlashAttribute("success", "ลบสาขาเรียบร้อยแล้ว");
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            redirectAttributes.addFlashAttribute("error", "ลบไม่ได้ เพราะสาขานี้ยังมีเครื่องหรือออเดอร์ที่ใช้งานอยู่");
        }
        return LIST_REDIRECT;
    }
}
