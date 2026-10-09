package com.laundryhub.service.state;

import com.laundryhub.domain.enums.MachineStatus;
import org.springframework.stereotype.Component;

@Component
public class InUseState implements MachineState {
    @Override
    public MachineStatus status() {
        return MachineStatus.IN_USE;
    }

    @Override
    public boolean canStart() {
        return false;
    }

    @Override
    public boolean canFinish() {
        return true;
    }

    @Override
    public boolean canSetOutOfService() {
        return false;
    }
}
