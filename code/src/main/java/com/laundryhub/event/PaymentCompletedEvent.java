package com.laundryhub.event;

import com.laundryhub.domain.enums.PayableType;

public record PaymentCompletedEvent(Long paymentId, Long userId, PayableType type, Long refId) {
}
