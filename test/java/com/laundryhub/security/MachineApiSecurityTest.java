package com.laundryhub.security;

import com.laundryhub.config.SecurityConfig;
import com.laundryhub.controller.api.MachineApiController;
import com.laundryhub.domain.enums.*;
import com.laundryhub.dto.response.MachineResponse;
import com.laundryhub.exception.*;
import com.laundryhub.service.MachineService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@WebMvcTest(MachineApiController.class)
@Import({SecurityConfig.class, ApiErrorWriter.class, ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class, GlobalExceptionHandler.class})
class MachineApiSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean MachineService service;
    private static final String BODY = """
            {"branchId":1,"name":"Washer 1","machineType":"WASHER","basePrice":20,"pricePerMinute":1.5}
            """;
    private static MachineResponse response() {
        return new MachineResponse(5L, 1L, "Washer 1", MachineType.WASHER,
                MachineStatus.AVAILABLE, new BigDecimal("20"), new BigDecimal("1.50"));
    }

    @Test void anonymousGetsStandard401() throws Exception {
        mvc.perform(get("/api/v1/machines")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(service);
    }

    @Test void searchPassesFiltersPageAndSortAndReturnsStablePage() throws Exception {
        when(service.search(eq(1L), eq(MachineStatus.AVAILABLE), eq(MachineType.WASHER), any()))
                .thenAnswer(inv -> {
                    Pageable p = inv.getArgument(3);
                    assertEquals(2, p.getPageNumber());
                    assertEquals(5, p.getPageSize());
                    assertEquals(Sort.Direction.DESC, p.getSort().getOrderFor("name").getDirection());
                    return new PageImpl<>(List.of(response()), p, 11);
                });
        mvc.perform(get("/api/v1/machines?branchId=1&status=AVAILABLE&type=WASHER&page=2&size=5&sort=name,desc")
                        .with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(5))
                .andExpect(jsonPath("$.page").value(2)).andExpect(jsonPath("$.totalElements").value(11));
    }

    @ParameterizedTest @ValueSource(strings = {"CUSTOMER", "STAFF"})
    void nonAdminCannotCreateUpdateOrDelete(String role) throws Exception {
        var auth = user("user").roles(role);
        mvc.perform(post("/api/v1/machines").with(auth).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mvc.perform(put("/api/v1/machines/5").with(auth).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/machines/5").with(auth)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void adminCreatesUpdatesAndDeletes() throws Exception {
        when(service.create(any())).thenReturn(response());
        when(service.update(eq(5L), any())).thenReturn(response());
        mvc.perform(post("/api/v1/machines").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(5));
        mvc.perform(put("/api/v1/machines/5").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/machines/5").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).delete(5L);
    }

    @Test void invalidMoneyRejectedBeforeCreate() throws Exception {
        mvc.perform(post("/api/v1/machines").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("\"basePrice\":20", "\"basePrice\":-1")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("basePrice"));
        verifyNoInteractions(service);
    }

    @Test void staffChangesStatusCustomerCannotAndMissingStatusIs400() throws Exception {
        when(service.changeStatus(5L, MachineStatus.OUT_OF_SERVICE)).thenReturn(response());
        mvc.perform(patch("/api/v1/machines/5/status").with(user("customer").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OUT_OF_SERVICE\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/machines/5/status").with(user("staff").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OUT_OF_SERVICE\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/machines/5/status").with(user("staff").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(service, times(1)).changeStatus(any(), any());
    }

    @Test void missingMachineAndDuplicateNameUse404And409() throws Exception {
        when(service.findById(99L)).thenThrow(new ResourceNotFoundException("Machine not found"));
        when(service.create(any())).thenThrow(new DuplicateResourceException("Machine name exists"));
        mvc.perform(get("/api/v1/machines/99").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mvc.perform(post("/api/v1/machines").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
    }
}
