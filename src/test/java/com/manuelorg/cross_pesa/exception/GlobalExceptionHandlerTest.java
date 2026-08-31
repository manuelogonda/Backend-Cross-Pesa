package com.manuelorg.cross_pesa.exception;

import com.manuelorg.cross_pesa.exception.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBadRequest_returnsGenericMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/test");
        var response = handler.handleBadRequest(new IllegalArgumentException("Email already exists"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(ReflectionTestUtils.getField(body, "message")).isEqualTo("Invalid request");
    }

    @Test
    void handleInsufficientBalance_returnsGenericMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/test");
        var response = handler.handleInsufficientBalance(new InsufficientBalanceException("Available balance is 12.50"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(ReflectionTestUtils.getField(body, "message")).isEqualTo("Insufficient balance");
    }

    @Test
    void handleGlobalException_returnsGenericServerMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/test");
        var response = handler.handleGlobalException(new RuntimeException("sql failed with table name"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(ReflectionTestUtils.getField(body, "message"))
                .isEqualTo("An unexpected internal server error occurred");
    }

    @Test
    void handleValidationExceptions_keepsFieldErrors() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/test");
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("validationTarget", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "Email is invalid"));

        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(parameter, bindingResult);
        var response = handler.handleValidationExceptions(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(ReflectionTestUtils.getField(body, "message"))
                .isEqualTo("Validation failed for one or more fields");
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) ReflectionTestUtils.getField(body, "validationErrors");
        assertThat(errors).containsEntry("email", "Email is invalid");
    }

    @Test
    void handleResourceNotFound_returnsGenericMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        var response = handler.handleResourceNotFound(new ResourceNotFoundException("Transaction abc123 not found"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(ReflectionTestUtils.getField(body, "message")).isEqualTo("Resource not found");
    }

    @SuppressWarnings("unused")
    private void validationTarget(String value) {
        // Used only to construct a MethodParameter for validation exception tests.
    }
}
