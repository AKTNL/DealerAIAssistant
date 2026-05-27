package com.brand.agentpoc.dto.response;

import java.util.List;
import java.util.Map;

public record DataQueryResponse(
        String dataset,
        Map<String, String> filters,
        int count,
        List<Map<String, Object>> items,
        Map<String, Object> metadata
) {
}
