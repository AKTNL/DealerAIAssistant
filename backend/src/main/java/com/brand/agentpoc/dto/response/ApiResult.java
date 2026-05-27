package com.brand.agentpoc.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResult<T>(int code, T data, String message) {

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(200, data, "success");
    }

    public static <T> ApiResult<T> error(int code, String message) {
        return new ApiResult<>(code, null, message);
    }
}
