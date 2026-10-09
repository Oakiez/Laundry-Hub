package com.laundryhub.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void notFound_returns404() throws Exception {
        mockMvc.perform(get("/t/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Order 99 not found"))
                .andExpect(jsonPath("$.path").value("/t/not-found"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void businessRule_returns400() throws Exception {
        mockMvc.perform(get("/t/business"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("bad transition"));
    }

    @Test
    void duplicate_returns409() throws Exception {
        mockMvc.perform(get("/t/duplicate")).andExpect(status().isConflict());
    }

    @Test
    void bookingConflict_returns409() throws Exception {
        mockMvc.perform(get("/t/booking")).andExpect(status().isConflict());
    }

    @Test
    void dataIntegrity_returns409_withoutLeakingDbMessage() throws Exception {
        mockMvc.perform(get("/t/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Request conflicts with existing data"));
    }

    @Test
    void accessDenied_returns403() throws Exception {
        mockMvc.perform(get("/t/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void unexpected_returns500_withoutInternalDetails() throws Exception {
        mockMvc.perform(get("/t/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @Test
    void invalidBody_returns400_withFieldErrors() throws Exception {
        mockMvc.perform(post("/t/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/t/validate").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongHttpMethod_returns405_notFiveHundred() throws Exception {
        mockMvc.perform(post("/t/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void unknownUrl_returns404_notFiveHundred() throws Exception {
        mockMvc.perform(get("/t/no-such-page"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Resource not found"));
    }

    @Test
    void missingRequiredParam_returns400() throws Exception {
        mockMvc.perform(get("/t/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter 'q'"));
    }

    @Test
    void unsupportedContentType_returns415() throws Exception {
        mockMvc.perform(post("/t/validate").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }

    record Body(@NotBlank String name) {
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/t/not-found")
        void notFound() {
            throw new ResourceNotFoundException("Order 99 not found");
        }

        @GetMapping("/t/business")
        void business() {
            throw new BusinessRuleException("bad transition");
        }

        @GetMapping("/t/duplicate")
        void duplicate() {
            throw new DuplicateResourceException("already paid");
        }

        @GetMapping("/t/booking")
        void booking() {
            throw new BookingConflictException("slot taken");
        }

        @GetMapping("/t/integrity")
        void integrity() {
            throw new DataIntegrityViolationException("duplicate key value violates constraint payments_order_id_key");
        }

        @GetMapping("/t/denied")
        void denied() {
            throw new AccessDeniedException("nope");
        }

        @GetMapping("/t/no-such-page")
        void noSuchPage() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/t/no-such-page");
        }

        @GetMapping("/t/boom")
        void boom() {
            throw new IllegalStateException("secret internal detail");
        }

        @GetMapping("/t/param")
        void param(@RequestParam String q) {
        }

        @PostMapping("/t/validate")
        void validate(@Valid @RequestBody Body body) {
        }
    }
}
