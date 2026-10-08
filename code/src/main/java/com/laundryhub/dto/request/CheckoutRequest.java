package com.laundryhub.dto.request;

import com.laundryhub.domain.enums.PayableType;
import com.laundryhub.domain.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** ไม่มี amount โดยตั้งใจ — จำนวนเงินมาจากฝั่งเซิร์ฟเวอร์ (Payable) เท่านั้น */
@Schema(description = "คำขอชำระเงิน")
public record CheckoutRequest(
        @NotNull PayableType payableType,
        @NotNull @Positive Long payableId,
        @NotNull PaymentMethod method) {
}
