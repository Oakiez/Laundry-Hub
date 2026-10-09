package com.laundryhub.controller.web;

import com.laundryhub.common.SecurityUtils;
import com.laundryhub.domain.enums.*;
import com.laundryhub.dto.request.BookSessionRequest;
import com.laundryhub.dto.request.SessionBookingForm;
import com.laundryhub.exception.BookingConflictException;
import com.laundryhub.exception.BusinessRuleException;
import com.laundryhub.service.BranchService;
import com.laundryhub.service.MachineService;
import com.laundryhub.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Shares booking, pricing, locking and ownership rules with the REST controllers. */
@Controller
@PreAuthorize("isAuthenticated()")
public class SelfServiceWebController {
    private final MachineService machines;
    private final SessionService sessions;
    private final BranchService branches;

    public SelfServiceWebController(MachineService machines, SessionService sessions, BranchService branches) {
        this.machines = machines;
        this.sessions = sessions;
        this.branches = branches;
    }

    @GetMapping("/machines")
    public String board(@RequestParam(required = false) Long branchId,
                        @RequestParam(required = false) MachineStatus status,
                        @RequestParam(required = false) MachineType type,
                        @RequestParam(defaultValue = "0") int page, Model model) {
        return boardModel(branchId, status, type, page, false, model);
    }

    @GetMapping("/staff/machines")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public String staffBoard(@RequestParam(required = false) Long branchId,
                             @RequestParam(required = false) MachineStatus status,
                             @RequestParam(required = false) MachineType type,
                             @RequestParam(defaultValue = "0") int page, Model model) {
        return boardModel(branchId, status, type, page, true, model);
    }

    private String boardModel(Long branchId, MachineStatus status, MachineType type, int page,
                              boolean staffBoard, Model model) {
        model.addAttribute("machines", machines.search(branchId, status, type,
                PageRequest.of(Math.max(0, page), 12, Sort.by("name", "id"))));
        model.addAttribute("branches", branches.findAll());
        model.addAttribute("branchId", branchId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedType", type);
        model.addAttribute("statuses", MachineStatus.values());
        model.addAttribute("types", MachineType.values());
        model.addAttribute("staffBoard", staffBoard);
        model.addAttribute("boardPath", staffBoard ? "/staff/machines" : "/machines");
        return "machines/list";
    }

    @GetMapping("/machines/{id}")
    public String detail(@PathVariable Long id, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("machine", machines.findById(id));
        if (isStaff()) {
            model.addAttribute("sessions", sessions.findForMachine(id, true,
                    PageRequest.of(Math.max(0, page), 10, Sort.by(Sort.Direction.DESC, "startTime", "id"))));
        }
        return "machines/detail";
    }

    @GetMapping("/machines/{id}/book")
    @PreAuthorize("hasRole('CUSTOMER')")
    public String bookingForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", new SessionBookingForm());
        return bookingModel(id, model);
    }

    @PostMapping("/machines/{id}/book")
    @PreAuthorize("hasRole('CUSTOMER')")
    public String book(@PathVariable Long id, @Valid @ModelAttribute("form") SessionBookingForm form,
                       BindingResult result, Model model, RedirectAttributes flash) {
        if (result.hasErrors()) return bookingModel(id, model);
        try {
            sessions.book(id, SecurityUtils.currentUserId(), false,
                    new BookSessionRequest(SecurityUtils.currentUserId(), form.getStartTime(), form.getDurationMinutes()));
        } catch (BookingConflictException ex) {
            result.reject("booking.overlap", "ช่วงเวลานี้มีผู้จองแล้ว กรุณาเลือกเวลาใหม่");
            return bookingModel(id, model);
        } catch (BusinessRuleException ex) {
            result.reject("booking.invalid", "จองไม่ได้ กรุณาตรวจเวลาเริ่ม ระยะเวลา และสถานะเครื่อง");
            return bookingModel(id, model);
        }
        flash.addFlashAttribute("success", "จองเครื่องเรียบร้อยแล้ว");
        return "redirect:/sessions/history";
    }

    private String bookingModel(Long id, Model model) {
        model.addAttribute("machine", machines.findById(id));
        return "machines/book";
    }

    @GetMapping("/sessions/history")
    @PreAuthorize("hasRole('CUSTOMER')")
    public String history(@RequestParam(defaultValue = "0") int page, Model model) {
        Long owner = SecurityUtils.currentUserId();
        model.addAttribute("sessions", sessions.findForUser(owner, owner, false,
                PageRequest.of(Math.max(0, page), 10, Sort.by(Sort.Direction.DESC, "startTime", "id"))));
        return "sessions/history";
    }

    @PostMapping("/sessions/{id}/start")
    @PreAuthorize("hasAnyRole('CUSTOMER','STAFF','ADMIN')")
    public String start(@PathVariable Long id, RedirectAttributes flash) {
        try {
            sessions.start(id, SecurityUtils.currentUserId(), isStaff());
            flash.addFlashAttribute("success", "เริ่มใช้งานเครื่องแล้ว");
        } catch (BusinessRuleException | BookingConflictException ex) {
            flash.addFlashAttribute("error", "เริ่มไม่ได้ กรุณาตรวจสถานะรอบและเครื่องอีกครั้ง");
        }
        return lifecycleRedirect();
    }

    @PostMapping("/sessions/{id}/finish")
    @PreAuthorize("hasAnyRole('CUSTOMER','STAFF','ADMIN')")
    public String finish(@PathVariable Long id, RedirectAttributes flash) {
        try {
            sessions.finish(id, SecurityUtils.currentUserId(), isStaff());
            flash.addFlashAttribute("success", "จบรอบใช้งานแล้ว");
        } catch (BusinessRuleException | BookingConflictException ex) {
            flash.addFlashAttribute("error", "จบไม่ได้ กรุณาตรวจสถานะรอบอีกครั้ง");
        }
        return lifecycleRedirect();
    }

    @PostMapping("/sessions/{id}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER','STAFF','ADMIN')")
    public String cancel(@PathVariable Long id, RedirectAttributes flash) {
        try {
            sessions.cancel(id, SecurityUtils.currentUserId(), isStaff());
            flash.addFlashAttribute("success", "ยกเลิกการจองแล้ว");
        } catch (BusinessRuleException | BookingConflictException ex) {
            flash.addFlashAttribute("error", "ยกเลิกได้เฉพาะรอบที่ยังไม่ได้เริ่มใช้งาน");
        }
        return lifecycleRedirect();
    }

    @PostMapping("/staff/machines/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public String changeStatus(@PathVariable Long id, @RequestParam MachineStatus status, RedirectAttributes flash) {
        try {
            machines.changeStatus(id, status);
            flash.addFlashAttribute("success", "เปลี่ยนสถานะเครื่องแล้ว");
        } catch (BusinessRuleException | BookingConflictException ex) {
            flash.addFlashAttribute("error", "เปลี่ยนไม่ได้ เครื่องต้องไม่อยู่ระหว่างใช้งาน และเลือกได้เฉพาะพร้อมใช้หรือปิดซ่อม");
        }
        return "redirect:/staff/machines";
    }

    private boolean isStaff() {
        return SecurityUtils.currentRole() == Role.STAFF || SecurityUtils.currentRole() == Role.ADMIN;
    }

    private String lifecycleRedirect() {
        return isStaff() ? "redirect:/staff/machines" : "redirect:/sessions/history";
    }
}
