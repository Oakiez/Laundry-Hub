package com.laundryhub.service.impl;

import com.laundryhub.domain.Payable;
import com.laundryhub.exception.ResourceNotFoundException;
import com.laundryhub.repository.UsageSessionRepository;
import com.laundryhub.service.SessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SessionServiceImpl implements SessionService {
    private final UsageSessionRepository sessionRepository;

    public SessionServiceImpl(UsageSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    public Payable findPayable(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session " + sessionId + " not found"));
    }
}
