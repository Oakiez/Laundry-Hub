package com.laundryhub.service.state;

import com.laundryhub.domain.enums.MachineStatus;
import com.laundryhub.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MachineStateTest {
    private final MachineStateFactory factory = new MachineStateFactory(List.of(
            new AvailableState(), new ReservedState(), new InUseState(), new OutOfServiceState()));

    @ParameterizedTest
    @CsvSource({"AVAILABLE,true,false,true", "RESERVED,false,false,true",
            "IN_USE,false,true,false", "OUT_OF_SERVICE,false,false,true"})
    void stateCapabilitiesFollowMachineLifecycle(MachineStatus status,
            boolean start, boolean finish, boolean outOfService) {
        MachineState state = factory.getState(status);
        assertEquals(status, state.status());
        assertEquals(start, state.canStart());
        assertEquals(finish, state.canFinish());
        assertEquals(outOfService, state.canSetOutOfService());
    }

    @Test
    void rejectsMissingStatus() {
        assertThrows(BusinessRuleException.class, () -> factory.getState(null));
    }

    @Test
    void rejectsIncompleteStateRegistration() {
        assertThrows(IllegalStateException.class,
                () -> new MachineStateFactory(List.of(new AvailableState())));
    }

    @Test
    void rejectsDuplicateStateRegistration() {
        assertThrows(IllegalStateException.class,
                () -> new MachineStateFactory(List.of(new AvailableState(), new AvailableState())));
    }
}
