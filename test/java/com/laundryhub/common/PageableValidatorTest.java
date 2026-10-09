package com.laundryhub.common;

import com.laundryhub.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageableValidatorTest {

    private static final Set<String> ALLOWED = Set.of("id", "createdAt");

    @Test
    void unsorted_isAccepted() {
        assertDoesNotThrow(() -> PageableValidator.requireSortableBy(PageRequest.of(0, 10), ALLOWED));
    }

    @Test
    void allowedFields_areAccepted() {
        PageRequest request = PageRequest.of(0, 10, Sort.by("createdAt").descending().and(Sort.by("id")));

        assertDoesNotThrow(() -> PageableValidator.requireSortableBy(request, ALLOWED));
    }

    @Test
    void unknownField_throwsBusinessRule_namingTheFieldAndTheAllowedOnes() {
        PageRequest request = PageRequest.of(0, 10, Sort.by("password"));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> PageableValidator.requireSortableBy(request, ALLOWED));
        assertTrue(ex.getMessage().contains("password"));
        assertTrue(ex.getMessage().contains("createdAt"));
    }

    @Test
    void oneBadFieldAmongGoodOnes_isRejected() {
        PageRequest request = PageRequest.of(0, 10, Sort.by("id", "abc"));

        assertThrows(BusinessRuleException.class,
                () -> PageableValidator.requireSortableBy(request, ALLOWED));
    }
}
