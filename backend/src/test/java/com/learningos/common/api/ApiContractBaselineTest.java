package com.learningos.common.api;

import com.learningos.common.exception.ApiException;
import com.learningos.common.exception.GlobalExceptionHandler;
import com.learningos.common.trace.TraceFilter;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ApiContractBaselineTest.ContractProbeController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        }
)
@Import({
        TraceFilter.class,
        GlobalExceptionHandler.class
})
class ApiContractBaselineTest {

    private static final String SAFE_TRACE_ID = "trc_contract_ok";
    private static final String SAFE_TRACE_ID_PATTERN = "^[A-Za-z0-9._:-]{1,128}$";

    private final MockMvc mockMvc;

    ApiContractBaselineTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void successResponseKeepsEnvelopeAndTraceHeader() throws Exception {
        mockMvc.perform(get("/api/contract-test/success")
                        .header(TraceFilter.TRACE_HEADER, SAFE_TRACE_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceFilter.TRACE_HEADER, SAFE_TRACE_ID))
                .andExpect(jsonPath("$.code").value(ErrorCode.OK.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.OK.defaultMessage()))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void validationErrorKeepsEnvelopeWithoutDataAndTraceHeader() throws Exception {
        mockMvc.perform(get("/api/contract-test/validated")
                        .header(TraceFilter.TRACE_HEADER, "trc_contract_validation")
                        .param("size", "0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(TraceFilter.TRACE_HEADER, "trc_contract_validation"))
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.name()))
                .andExpect(jsonPath("$.message").value("size must be at least 1"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void apiExceptionKeepsEnvelopeWithoutDataAndTraceHeader() throws Exception {
        mockMvc.perform(get("/api/contract-test/conflict")
                        .header(TraceFilter.TRACE_HEADER, "trc_contract_conflict")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(header().string(TraceFilter.TRACE_HEADER, "trc_contract_conflict"))
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT.name()))
                .andExpect(jsonPath("$.message").value("duplicate request"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void unexpectedExceptionKeepsSafeEnvelopeAndDoesNotLeakRawMessage() throws Exception {
        String responseBody = mockMvc.perform(get("/api/contract-test/unexpected")
                        .header(TraceFilter.TRACE_HEADER, "trc_contract_unexpected")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(TraceFilter.TRACE_HEADER, "trc_contract_unexpected"))
                .andExpect(jsonPath("$.code").value(ErrorCode.INTERNAL_ERROR.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_ERROR.defaultMessage()))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(responseBody).doesNotContain("secret-token", "jdbc:mysql://private-host", "raw provider stacktrace");
    }

    @Test
    void unsafeTraceHeaderIsReplacedWithSafeGeneratedTraceId() throws Exception {
        var response = mockMvc.perform(get("/api/contract-test/success")
                        .header(TraceFilter.TRACE_HEADER, "bad trace with spaces")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceFilter.TRACE_HEADER, matchesPattern(SAFE_TRACE_ID_PATTERN)))
                .andExpect(jsonPath("$.code").value(ErrorCode.OK.name()))
                .andReturn()
                .getResponse();

        assertThat(response.getHeader(TraceFilter.TRACE_HEADER)).isNotEqualTo("bad trace with spaces");
    }

    @Validated
    @RestController
    @RequestMapping("/api/contract-test")
    static class ContractProbeController {

        @GetMapping("/success")
        ApiResponse<Map<String, String>> success() {
            return ApiResponse.success(Map.of("status", "UP"));
        }

        @GetMapping("/validated")
        ApiResponse<Map<String, Integer>> validated(@RequestParam @Min(value = 1, message = "size must be at least 1") int size) {
            return ApiResponse.success(Map.of("size", size));
        }

        @GetMapping("/conflict")
        ApiResponse<Object> conflict() {
            throw new ApiException(ErrorCode.CONFLICT, "duplicate request");
        }

        @GetMapping("/unexpected")
        ApiResponse<Object> unexpected() {
            throw new IllegalStateException("secret-token jdbc:mysql://private-host raw provider stacktrace");
        }
    }
}
