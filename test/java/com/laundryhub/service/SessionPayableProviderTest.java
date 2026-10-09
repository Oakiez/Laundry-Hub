package com.laundryhub.service;

import com.laundryhub.domain.entity.UsageSession;
import com.laundryhub.domain.entity.User;
import com.laundryhub.domain.enums.PayableType;
import com.laundryhub.exception.ResourceNotFoundException;
import com.laundryhub.repository.UsageSessionRepository;
import com.laundryhub.service.impl.SessionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionPayableProviderTest {
    @Mock UsageSessionRepository repository;
    private SessionPayableProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SessionPayableProvider(new SessionServiceImpl(repository,
                mock(com.laundryhub.repository.MachineRepository.class),
                mock(com.laundryhub.repository.UserRepository.class),
                mock(BookingValidator.class), mock(com.laundryhub.service.pricing.SelfServicePricing.class),
                mock(com.laundryhub.service.state.MachineStateFactory.class),
                new com.laundryhub.mapper.SessionMapper(),
                mock(org.springframework.context.ApplicationEventPublisher.class)));
    }

    @Test
    void supportsUsageSessions() {
        assertEquals(PayableType.USAGE_SESSION, provider.supports());
    }

    @Test
    void loadsPersistedSessionAmountAndOwnerThroughService() {
        User customer = new User();
        customer.setId(3L);
        UsageSession session = new UsageSession();
        ReflectionTestUtils.setField(session, "id", 9L);
        session.setUser(customer);
        session.setAmount(new BigDecimal("40.00"));
        when(repository.findById(9L)).thenReturn(Optional.of(session));
        var payable = provider.findPayable(9L);
        assertEquals(9L, payable.getId());
        assertEquals(0, new BigDecimal("40.00").compareTo(payable.getPayableAmount()));
        assertEquals(PayableType.USAGE_SESSION, payable.getPayableType());
        assertEquals(3L, payable.getOwnerUserId());
        verify(repository).findById(9L);
    }

    @Test
    void missingSessionPropagatesNotFoundThroughProvider() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        var exception = assertThrows(ResourceNotFoundException.class, () -> provider.findPayable(99L));
        assertEquals("Session 99 not found", exception.getMessage());
    }
}
