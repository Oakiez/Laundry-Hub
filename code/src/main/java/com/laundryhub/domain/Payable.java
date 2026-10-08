package com.laundryhub.domain;

import com.laundryhub.domain.enums.PayableType;

import java.math.BigDecimal;

public interface Payable {

    Long getId();

    BigDecimal getPayableAmount();

    PayableType getPayableType();

    Long getOwnerUserId();
}
