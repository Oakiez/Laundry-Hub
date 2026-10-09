package com.laundryhub.controller.api;

import com.laundryhub.common.PageableValidator;
import com.laundryhub.common.SecurityUtils;
import com.laundryhub.domain.enums.PaymentStatus;
import com.laundryhub.domain.enums.Role;
import com.laundryhub.dto.request.CheckoutRequest;
import com.laundryhub.dto.response.PaymentResponse;
import com.laundryhub.mapper.PaymentMapper;
import com.laundryhub.service.PaymentService;
import com.laundryhub.service.payment.CheckoutFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments")
public class PaymentApiController {

    private static final String STAFF_OR_ADMIN = "hasAnyRole('STAFF','ADMIN')";
    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "createdAt", "paidAt", "amount", "status", "method");

    private final CheckoutFacade checkoutFacade;
    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;

    public PaymentApiController(CheckoutFacade checkoutFacade,
                                PaymentService paymentService,
                                PaymentMapper paymentMapper) {
        this.checkoutFacade = checkoutFacade;
        this.paymentService = paymentService;
        this.paymentMapper = paymentMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Pay for an order or machine session (owner or staff)")
    public PaymentResponse checkout(@Valid @RequestBody CheckoutRequest request) {
        return checkoutFacade.checkout(request, SecurityUtils.currentUserId(), isStaff());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a payment (owner or staff)")
    public PaymentResponse getPayment(@PathVariable Long id) {
        return checkoutFacade.getPayment(id, SecurityUtils.currentUserId(), isStaff());
    }

    @GetMapping
    @PreAuthorize(STAFF_OR_ADMIN)
    @Operation(summary = "List payments, optionally by status (staff/admin, paged)")
    public PagedModel<PaymentResponse> list(
            @RequestParam(required = false) PaymentStatus status,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        PageableValidator.requireSortableBy(pageable, SORTABLE_FIELDS);
        return new PagedModel<>(paymentService.findAll(status, pageable).map(paymentMapper::toResponse));
    }

    @PatchMapping("/{id}/confirm")
    @PreAuthorize(STAFF_OR_ADMIN)
    @Operation(summary = "Confirm a pending (cash) payment (staff/admin)")
    public PaymentResponse confirm(@PathVariable Long id) {
        return checkoutFacade.confirmPayment(id);
    }

    private static boolean isStaff() {
        return SecurityUtils.currentRole() != Role.CUSTOMER;
    }
}
