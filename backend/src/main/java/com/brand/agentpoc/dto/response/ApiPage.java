package com.brand.agentpoc.dto.response;

import java.util.List;

public record ApiPage<T>(List<T> items, long total, int page, int pageSize) {

    public static <T> ApiPage<T> of(List<T> items, long total, int page, int pageSize) {
        return new ApiPage<>(items, total, page, pageSize);
    }

    public static <T> ApiPage<T> empty(int page, int pageSize, long total) {
        return new ApiPage<>(List.of(), total, page, pageSize);
    }
}
