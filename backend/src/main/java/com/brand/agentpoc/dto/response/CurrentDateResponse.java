package com.brand.agentpoc.dto.response;

public record CurrentDateResponse(
        String currentDate,
        int currentYear,
        int currentMonth,
        int currentQuarter
) {
}
