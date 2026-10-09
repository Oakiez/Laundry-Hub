package com.laundryhub.service.state;

import com.laundryhub.domain.enums.MachineStatus;
import org.springframework.stereotype.Component;

@Component
public class AvailableState implements MachineState {
    @Override
    public MachineStatus status() {
        return MachineStatus.AVAILABLE;
    }

    @Override
    public boolean canStart() {
        return true;
    }

    @Override
    public boolean canFinish() {
        return false;
    }

    @Override
    public boolean canSetOutOfService() {
        return true;
    }
}
