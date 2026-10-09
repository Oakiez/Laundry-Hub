package com.laundryhub.event;

import com.laundryhub.domain.enums.SessionStatus;

public record SessionStatusChangedEvent(Long sessionId, Long userId, SessionStatus newStatus) {
}
