package com.laundryhub.controller.web;

import com.laundryhub.common.SecurityUtils;
import com.laundryhub.domain.enums.PayableType;
import com.laundryhub.domain.enums.PaymentMethod;
import com.laundryhub.domain.enums.PaymentStatus;
import com.laundryhub.domain.enums.Role;
import com.laundryhub.dto.request.CheckoutRequest;
import com.laundryhub.dto.response.PaymentResponse;
import com.laundryhub.exception.BusinessRuleException;
import com.laundryhub.exception.DuplicateResourceException;
import com.laundryhub.exception.ResourceNotFoundException;
import com.laundryhub.mapper.PaymentMapper;
import com.laundryhub.service.PaymentService;
import com.laundryhub.service.payment.CheckoutFacade;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/**
 * Payment pages. Like the REST API, every action goes through {@link CheckoutFacade}, so the
 * owner/staff rule lives in one place. Errors are caught here and shown as flash messages
 * (GlobalExceptionHandler would otherwise turn them into JSON, which is wrong for a web page).
 */
@Controller
public class PaymentWebController {

    private static final String NEW_VIEW = "payments/new";
    private static final String NEW_REDIRECT = "redirect:/payments/new";
    private static final String STAFF_LIST_REDIRECT = "redirect:/staff/payments";
    private static final int PAGE_SIZE = 10;

    private static final Map<PayableType, String> TYPE_LABELS = Map.of(
            PayableType.LAUNDRY_ORDER, "ออเดอร์ฝากซัก",
            PayableType.USAGE_SESSION, "รอบใช้เครื่อง");
    private static final Map<PaymentMethod, String> METHOD_LABELS = Map.of(
            PaymentMethod.CASH, "เงินสด",
            PaymentMethod.QR_MOCK, "QR (จำลอง)",
            PaymentMethod.COIN, "หยอดเหรียญ");
    /** Longer text for the payment form only; tables and the detail page use the short labels above. */
    private static final Map<PaymentMethod, String> METHOD_OPTION_LABELS = Map.of(
            PaymentMethod.CASH, "เงินสด (รอพนักงานยืนยันรับเงิน)",
            PaymentMethod.QR_MOCK, "QR (จำลอง) ชำระสำเร็จทันที",
            PaymentMethod.COIN, "หยอดเหรียญ (เฉพาะรอบใช้เครื่อง)");
    private static final Map<PaymentStatus, String> STATUS_LABELS = Map.of(
            PaymentStatus.PENDING, "รอยืนยัน",
            PaymentStatus.PAID, "ชำระแล้ว",
            PaymentStatus.FAILED, "ไม่สำเร็จ");

    private final CheckoutFacade checkoutFacade;
    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;

    public PaymentWebController(CheckoutFacade checkoutFacade,
                                PaymentService paymentService,
                                PaymentMapper paymentMapper) {
        this.checkoutFacade = checkoutFacade;
        this.paymentService = paymentService;
        this.paymentMapper = paymentMapper;
    }

    /** Labels and option lists shared by every page of this controller. */
    @ModelAttribute
    public void addCommonAttributes(Model model) {
        model.addAttribute("typeLabels", TYPE_LABELS);
        model.addAttribute("methodLabels", METHOD_LABELS);
        model.addAttribute("methodOptionLabels", METHOD_OPTION_LABELS);
        model.addAttribute("statusLabels", STATUS_LABELS);
        model.addAttribute("types", PayableType.values());
        model.addAttribute("methods", PaymentMethod.values());
    }

    @GetMapping("/payments/new")
    public String newForm(@RequestParam(required = false) PayableType type,
                          @RequestParam(required = false) Long id,
                          Model model) {
        model.addAttribute("form", new CheckoutRequest(type, id, null));
        return NEW_VIEW;
    }

    @PostMapping("/payments")
    public String checkout(@Valid @ModelAttribute("form") CheckoutRequest form, BindingResult result,
                           Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return NEW_VIEW;
        }
        try {
            PaymentResponse payment = checkoutFacade.checkout(form, SecurityUtils.currentUserId(), isStaff());
            redirectAttributes.addFlashAttribute("success", payment.status() == PaymentStatus.PAID
                    ? "ชำระเงินเรียบร้อยแล้ว"
                    : "บันทึกการชำระแล้ว กรุณารอพนักงานยืนยันการรับเงิน");
            return "redirect:/payments/" + payment.id();
        } catch (AccessDeniedException e) {
            model.addAttribute("error", "คุณชำระได้เฉพาะรายการของตัวเองเท่านั้น");
        } catch (ResourceNotFoundException | DuplicateResourceException | BusinessRuleException e) {
            model.addAttribute("error", e.getMessage());
        }
        return NEW_VIEW;
    }

    @GetMapping("/payments/{id:\\d+}")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("payment", checkoutFacade.getPayment(id, SecurityUtils.currentUserId(), isStaff()));
            return "payments/detail";
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "คุณดูได้เฉพาะการชำระเงินของตัวเองเท่านั้น");
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return NEW_REDIRECT;
    }

    /** Staff/admin only: the /staff/** URL rule in SecurityConfig covers both methods below. */
    @GetMapping("/staff/payments")
    public String list(@RequestParam(required = false) PaymentStatus status,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PaymentResponse> payments = paymentService.findAll(status, pageable).map(paymentMapper::toResponse);
        model.addAttribute("payments", payments);
        model.addAttribute("status", status);
        model.addAttribute("statuses", PaymentStatus.values());
        return "payments/list";
    }

    @PostMapping("/staff/payments/{id:\\d+}/confirm")
    public String confirm(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            checkoutFacade.confirmPayment(id);
            redirectAttributes.addFlashAttribute("success", "ยืนยันการรับเงินเรียบร้อยแล้ว");
            return "redirect:/payments/" + id;
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return STAFF_LIST_REDIRECT;
        } catch (DuplicateResourceException e) {
            redirectAttributes.addFlashAttribute("error", "รายการนี้ชำระแล้ว");
            return "redirect:/payments/" + id;
        }
    }

    private static boolean isStaff() {
        return SecurityUtils.currentRole() != Role.CUSTOMER;
    }
}
