package com.laundryhub.dto.request;

import com.laundryhub.domain.enums.MachineStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeMachineStatusRequest(@NotNull MachineStatus status) {
}
