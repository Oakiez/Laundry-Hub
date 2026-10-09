package com.laundryhub.service;

import com.laundryhub.domain.Payable;

public interface SessionService {
    /** Payment lookup; throws ResourceNotFoundException when the session does not exist. */
    Payable findPayable(Long sessionId);
}
