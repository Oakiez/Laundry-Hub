package com.laundryhub.dto.response;

import com.laundryhub.domain.enums.MachineStatus;
import com.laundryhub.domain.enums.MachineType;
import java.math.BigDecimal;

public record MachineResponse(Long id, Long branchId, String name, MachineType machineType,
                              MachineStatus status, BigDecimal basePrice, BigDecimal pricePerMinute) {
}
