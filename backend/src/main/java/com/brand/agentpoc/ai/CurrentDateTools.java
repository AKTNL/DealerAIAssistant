package com.brand.agentpoc.ai;

import com.brand.agentpoc.dto.response.CurrentDateResponse;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class CurrentDateTools {

    private final Clock clock;

    public CurrentDateTools(Clock clock) {
        this.clock = clock;
    }

    @Tool(
            name = "getCurrentDate",
            description = "Return the current system date together with the current year, month, and quarter for time-scoped analytics."
    )
    public CurrentDateResponse getCurrentDate() {
        LocalDate today = LocalDate.now(clock);
        return new CurrentDateResponse(
                today.toString(),
                today.getYear(),
                today.getMonthValue(),
                ((today.getMonthValue() - 1) / 3) + 1
        );
    }
}
