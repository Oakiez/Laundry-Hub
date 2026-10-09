package com.laundryhub.mapper;

import com.laundryhub.domain.entity.Branch;
import com.laundryhub.domain.entity.Machine;
import com.laundryhub.dto.request.MachineRequest;
import com.laundryhub.dto.response.MachineResponse;
import org.springframework.stereotype.Component;

@Component
public class MachineMapper {
    public MachineResponse toResponse(Machine machine) {
        return new MachineResponse(machine.getId(), machine.getBranch().getId(), machine.getName(),
                machine.getMachineType(), machine.getStatus(), machine.getBasePrice(), machine.getPricePerMinute());
    }

    public void apply(Machine machine, Branch branch, MachineRequest request) {
        machine.setBranch(branch);
        machine.setName(request.name().strip());
        machine.setMachineType(request.machineType());
        machine.setBasePrice(request.basePrice());
        machine.setPricePerMinute(request.pricePerMinute());
    }
}
