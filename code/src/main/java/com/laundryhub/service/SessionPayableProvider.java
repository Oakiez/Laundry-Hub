package com.laundryhub.service;

import com.laundryhub.domain.Payable;
import com.laundryhub.domain.enums.PayableType;
import com.laundryhub.service.payment.PayableProvider;
import org.springframework.stereotype.Service;

/** Adapts session lookup to payments without exposing the session repository to checkout. */
@Service
public class SessionPayableProvider implements PayableProvider {
    private final SessionService sessionService;

    public SessionPayableProvider(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public PayableType supports() {
        return PayableType.USAGE_SESSION;
    }

    @Override
    public Payable findPayable(Long id) {
        return sessionService.findPayable(id);
    }
}
