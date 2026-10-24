package com.axionix.tasktracker.dto;

import com.axionix.tasktracker.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The one and only error shape returned by the API:
 * <pre>{ "error": { "code": "...", "message": "...", "details": [ { "field": "...", "message": "..." } ] } }</pre>
 */
public record ErrorResponse(Body error) {

    public record Body(String code,
                       String message,
                       @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldIssue> details) {
    }

    public record FieldIssue(String field, String message) {
    }

    public static ErrorResponse of(ErrorCode code, String message) {
        return new ErrorResponse(new Body(code.name(), message, null));
    }

    public static ErrorResponse of(ErrorCode code, String message, List<FieldIssue> details) {
        return new ErrorResponse(new Body(code.name(), message, details));
    }
}
