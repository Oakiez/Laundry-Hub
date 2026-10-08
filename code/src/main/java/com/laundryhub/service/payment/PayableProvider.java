package com.laundryhub.service.payment;

import com.laundryhub.domain.Payable;
import com.laundryhub.domain.enums.PayableType;

public interface PayableProvider {

    PayableType supports();

    /** @throws com.laundryhub.exception.ResourceNotFoundException when no payable has this id */
    Payable findPayable(Long id);
}
