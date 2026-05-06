package com.brand.agentpoc.ai;

import com.brand.agentpoc.dto.response.DataQueryResponse;
import com.brand.agentpoc.service.DataQueryService;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class TargetTools {

    private final DataQueryService dataQueryService;

    public TargetTools(DataQueryService dataQueryService) {
        this.dataQueryService = dataQueryService;
    }

    @Tool(
            name = "queryTargets",
            description = "Query target data for achievement analysis, benchmark comparison, and target-gap analysis."
    )
    public DataQueryResponse queryTargets(
            @ToolParam(description = "Optional exact dealer code filter such as BJ001.", required = false)
            String dealerCode,
            @ToolParam(description = "Optional exact city filter such as Beijing.", required = false)
            String city,
            @ToolParam(description = "Optional exact dealer group name filter.", required = false)
            String dealerGroupName,
            @ToolParam(description = "Optional product model filter such as M7 or X5.", required = false)
            String productModel,
            @ToolParam(description = "Optional target year, such as 2026.", required = false)
            Integer targetYear,
            @ToolParam(description = "Optional target month, from 1 to 12.", required = false)
            Integer targetMonth
    ) {
        Map<String, String> filters = ToolFilterSupport.newFilters();
        ToolFilterSupport.put(filters, "dealerCode", dealerCode);
        ToolFilterSupport.put(filters, "city", city);
        ToolFilterSupport.put(filters, "dealerGroupName", dealerGroupName);
        ToolFilterSupport.put(filters, "productModel", productModel);
        ToolFilterSupport.put(filters, "targetYear", targetYear);
        ToolFilterSupport.put(filters, "targetMonth", targetMonth);
        return dataQueryService.query("targets", filters);
    }
}
