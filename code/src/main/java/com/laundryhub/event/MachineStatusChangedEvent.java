package com.laundryhub.event;

import com.laundryhub.domain.enums.MachineStatus;

public record MachineStatusChangedEvent(Long machineId, MachineStatus newStatus) {
}
