package com.laundryhub.service.state;

import com.laundryhub.domain.enums.MachineStatus;
import org.springframework.stereotype.Component;

@Component
public class ReservedState implements MachineState {
    @Override
    public MachineStatus status() {
        return MachineStatus.RESERVED;
    }

    @Override
    public boolean canStart() {
        return false;
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
