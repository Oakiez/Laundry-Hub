package com.laundryhub.service.state;

import com.laundryhub.domain.enums.MachineStatus;

public interface MachineState {
    MachineStatus status();
    boolean canStart();
    boolean canFinish();
    boolean canSetOutOfService();
}
