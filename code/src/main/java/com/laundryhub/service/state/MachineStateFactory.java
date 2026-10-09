package com.laundryhub.service.state;

import com.laundryhub.domain.enums.MachineStatus;
import com.laundryhub.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class MachineStateFactory {
    private final Map<MachineStatus, MachineState> states = new EnumMap<>(MachineStatus.class);

    public MachineStateFactory(List<MachineState> implementations) {
        for (MachineState state : implementations) {
            if (states.putIfAbsent(state.status(), state) != null) {
                throw new IllegalStateException("Duplicate machine state: " + state.status());
            }
        }
        for (MachineStatus status : MachineStatus.values()) {
            if (!states.containsKey(status)) {
                throw new IllegalStateException("Missing machine state: " + status);
            }
        }
    }

    public MachineState getState(MachineStatus status) {
        if (status == null) {
            throw new BusinessRuleException("Machine status is required");
        }
        return states.get(status);
    }
}
