package com.manuelorg.cross_pesa.exception.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.Map;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL) // Hides null fields (like validationErrors) to keep JSON clean
public class ErrorResponse {
    private OffsetDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;

    // Only populated when a user fails @Valid checks (e.g., weak password)
    private Map<String, String> validationErrors;
}
