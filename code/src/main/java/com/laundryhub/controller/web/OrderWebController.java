package com.laundryhub.controller.web;

import com.laundryhub.common.SecurityUtils;
import com.laundryhub.domain.enums.OrderStatus;
import com.laundryhub.dto.request.ChangeStatusRequest;
import com.laundryhub.dto.request.CreateOrderRequest;
import com.laundryhub.dto.request.OrderForm;
import com.laundryhub.dto.response.OrderResponse;
import com.laundryhub.exception.BusinessRuleException;
import com.laundryhub.exception.ResourceNotFoundException;
import com.laundryhub.service.BranchService;
import com.laundryhub.service.OrderService;
import com.laundryhub.service.ServiceTypeService;
import com.laundryhub.service.state.OrderStateFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Order pages for customers (/orders) and the staff board (/staff/orders, limited to STAFF/ADMIN by the
 * /staff/** rule in SecurityConfig). Every action goes through OrderService, the same rules as the REST API.
 * Errors are caught here and shown as flash messages, because GlobalExceptionHandler answers with JSON.
 */
@Controller
public class OrderWebController {

    private static final int CUSTOMER_PAGE_SIZE = 10;
    private static final int BOARD_PAGE_SIZE = 20;

    /** The normal path of an order, used to draw the progress bar on the detail page. */
    private static final List<OrderStatus> FLOW = List.of(OrderStatus.RECEIVED, OrderStatus.WASHING,
            OrderStatus.DRYING, OrderStatus.IRONING, OrderStatus.READY, OrderStatus.PICKED_UP);

    private static final Map<OrderStatus, String> STATUS_LABELS = new EnumMap<>(Map.of(
            OrderStatus.RECEIVED, "รับผ้าแล้ว",
            OrderStatus.WASHING, "กำลังซัก",
            OrderStatus.DRYING, "กำลังอบ",
            OrderStatus.IRONING, "กำลังรีด",
            OrderStatus.READY, "พร้อมรับ",
            OrderStatus.PICKED_UP, "ลูกค้ารับผ้าคืนแล้ว",
            OrderStatus.CANCELLED, "ยกเลิก"));

    /** Next status per status, asked from the State classes, so the page never hard-codes the order of steps. */
    private static final Map<OrderStatus, OrderStatus> NEXT_STATUS = new EnumMap<>(OrderStatus.class);

    static {
        for (OrderStatus status : OrderStatus.values()) {
            OrderStateFactory.from(status).next().ifPresent(next -> NEXT_STATUS.put(status, next.status()));
        }
    }

    private final OrderService orderService;
    private final BranchService branchService;
    private final ServiceTypeService serviceTypeService;
    private final Validator validator;

    public OrderWebController(OrderService orderService, BranchService branchService,
                              ServiceTypeService serviceTypeService, Validator validator) {
        this.orderService = orderService;
        this.branchService = branchService;
        this.serviceTypeService = serviceTypeService;
        this.validator = validator;
    }

    @ModelAttribute
    public void addCommonAttributes(Model model) {
        model.addAttribute("statusLabels", STATUS_LABELS);
        model.addAttribute("nextStatus", NEXT_STATUS);
        model.addAttribute("statuses", OrderStatus.values());
    }

    // ---------- customer ----------

    @GetMapping("/orders")
    public String myOrders(@RequestParam(required = false) OrderStatus status,
                           @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("orders", orderService.listForCustomer(SecurityUtils.currentUserId(), status,
                PageRequest.of(Math.max(page, 0), CUSTOMER_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"))));
        model.addAttribute("status", status);
        return "orders/list";
    }

    @GetMapping("/orders/new")
    public String newForm(Model model) {
        return showForm(new OrderForm(), model);
    }

    @PostMapping("/orders")
    public String create(@ModelAttribute("form") OrderForm form, Model model, RedirectAttributes redirectAttributes) {
        CreateOrderRequest request = form.toRequest();
        // same Bean Validation rules as the REST API (@Valid on CreateOrderRequest)
        List<String> errors = validator.validate(request).stream().map(OrderWebController::toThaiMessage).toList();
        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            return showForm(form, model);
        }
        try {
            OrderResponse order = orderService.create(SecurityUtils.currentUserId(), request);
            redirectAttributes.addFlashAttribute("success", "สร้างออเดอร์ #" + order.id() + " เรียบร้อยแล้ว");
            return "redirect:/orders/" + order.id();
        } catch (ResourceNotFoundException | BusinessRuleException e) {
            model.addAttribute("error", e.getMessage());
            return showForm(form, model);
        }
    }

    @GetMapping("/orders/{id:\\d+}")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            OrderResponse order = orderService.getForCustomer(SecurityUtils.currentUserId(), id);
            model.addAttribute("order", order);
            model.addAttribute("flow", FLOW);
            model.addAttribute("currentStep", FLOW.indexOf(order.status()));
            return "orders/detail";
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/orders";
        }
    }

    @PostMapping("/orders/{id:\\d+}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            orderService.cancelByCustomer(SecurityUtils.currentUserId(), id);
            redirectAttributes.addFlashAttribute("success", "ยกเลิกออเดอร์ #" + id + " แล้ว");
        } catch (ResourceNotFoundException | BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/orders/" + id;
    }

    // ---------- staff board ----------

    @GetMapping("/staff/orders")
    public String board(@RequestParam(required = false) OrderStatus status,
                        @RequestParam(defaultValue = "0") int page, Model model) {
        // oldest first: the order that has waited longest is handled first
        model.addAttribute("orders", orderService.listAll(status,
                PageRequest.of(Math.max(page, 0), BOARD_PAGE_SIZE, Sort.by(Sort.Direction.ASC, "createdAt"))));
        model.addAttribute("status", status);
        return "orders/board";
    }

    @PostMapping("/staff/orders/{id:\\d+}/status")
    public String changeStatus(@PathVariable Long id, @RequestParam ChangeStatusRequest.Action action,
                               @RequestParam(required = false) OrderStatus filter,
                               RedirectAttributes redirectAttributes) {
        try {
            OrderResponse order = orderService.changeStatus(id, action);
            redirectAttributes.addFlashAttribute("success",
                    "ออเดอร์ #" + id + " → " + STATUS_LABELS.get(order.status()));
        } catch (ResourceNotFoundException | BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        if (filter != null) {
            redirectAttributes.addAttribute("status", filter);
        }
        return "redirect:/staff/orders";
    }

    private String showForm(OrderForm form, Model model) {
        model.addAttribute("form", form);
        model.addAttribute("branches", branchService.findAll());
        model.addAttribute("serviceTypes", serviceTypeService.findActive());
        return "orders/new";
    }

    /** Short Thai messages for the form; the REST API keeps the standard English field errors. */
    private static String toThaiMessage(ConstraintViolation<CreateOrderRequest> violation) {
        String path = violation.getPropertyPath().toString();
        if (path.equals("branchId")) {
            return "กรุณาเลือกสาขา";
        }
        if (path.equals("items")) {
            return "กรุณากรอกรายการผ้าอย่างน้อย 1 รายการ";
        }
        if (path.equals("note")) {
            return "หมายเหตุยาวได้ไม่เกิน 255 ตัวอักษร";
        }
        if (path.startsWith("items[")) {
            int row = Integer.parseInt(path.substring(6, path.indexOf(']'))) + 1;
            String field = path.substring(path.indexOf('.') + 1);
            String problem = switch (field) {
                case "serviceTypeId" -> "เลือกประเภทบริการ";
                case "itemName" -> "กรอกชื่อรายการ (ไม่เกิน 100 ตัวอักษร)";
                case "weightKg" -> "น้ำหนักต้องมากกว่า 0 และมีทศนิยมไม่เกิน 2 ตำแหน่ง";
                default -> violation.getMessage();
            };
            return "รายการที่ " + row + ": " + problem;
        }
        return violation.getMessage();
    }
}
