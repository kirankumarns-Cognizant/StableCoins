package com.cognizant.stablecoins.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    private final String code;
    private final String message;
    private final OffsetDateTime timestamp;
    private List<String> details;

    public ApiError(String code, String message) {
        this.code = code;
        this.message = message;
        this.timestamp = OffsetDateTime.now();
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public OffsetDateTime getTimestamp() { return timestamp; }
    public List<String> getDetails() { return details; }
    public void setDetails(List<String> details) { this.details = details; }
}
