package com.laundryhub.mapper;
import com.laundryhub.domain.entity.UsageSession;
import com.laundryhub.dto.response.SessionResponse;
import org.springframework.stereotype.Component;
@Component
public class SessionMapper {
    public SessionResponse toResponse(UsageSession s) {
        return new SessionResponse(s.getId(), s.getMachine().getId(), s.getOwnerUserId(), s.getStatus(),
                s.getStartTime(), s.getEndTime(), s.getDurationMinutes(), s.getAmount(), s.getCreatedAt());
    }
}
