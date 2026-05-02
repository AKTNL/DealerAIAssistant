package com.brand.agentpoc.dto.response;

import java.util.List;
import java.util.Map;

public record DataQueryResponse(
        String dataset,
        Map<String, String> filters,
        long totalCount,
        List<Map<String, Object>> items
) {
}

