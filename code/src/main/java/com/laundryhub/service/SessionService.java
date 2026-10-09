package com.laundryhub.service;

import com.laundryhub.domain.Payable;
import com.laundryhub.dto.request.BookSessionRequest;
import com.laundryhub.dto.response.SessionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface SessionService {
    SessionResponse book(Long machineId, Long currentUserId, boolean staff, @NotNull @Valid BookSessionRequest request);
    SessionResponse start(Long sessionId, Long currentUserId, boolean staff);
    SessionResponse finish(Long sessionId, Long currentUserId, boolean staff);
    SessionResponse cancel(Long sessionId, Long currentUserId, boolean staff);
    SessionResponse findById(Long sessionId, Long currentUserId, boolean staff);
    Page<SessionResponse> findForUser(Long userId, Long currentUserId, boolean staff, Pageable pageable);
    Page<SessionResponse> findForMachine(Long machineId, boolean staff, Pageable pageable);
    /** Payment lookup; throws ResourceNotFoundException when the session does not exist. */
    Payable findPayable(Long sessionId);
}
