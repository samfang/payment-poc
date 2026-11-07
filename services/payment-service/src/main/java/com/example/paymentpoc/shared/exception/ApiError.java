package com.example.paymentpoc.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Standard error envelope per API_GUIDELINES.md §7. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(ErrorBody error) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(
            String code,
            String message,
            String target,
            List<ErrorDetail> details,
            String debugId) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorDetail(
            String code,
            String message,
            String target) {}

    public static ApiError of(String code, String message, String debugId) {
        return new ApiError(new ErrorBody(code, message, null, null, debugId));
    }

    public static ApiError of(String code, String message, String target, String debugId) {
        return new ApiError(new ErrorBody(code, message, target, null, debugId));
    }
}
