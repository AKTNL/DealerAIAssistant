package com.brand.agentpoc.ai;

import com.brand.agentpoc.dto.response.DataQueryResponse;
import com.brand.agentpoc.service.DataQueryService;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class OpportunityTools {

    private final DataQueryService dataQueryService;

    public OpportunityTools(DataQueryService dataQueryService) {
        this.dataQueryService = dataQueryService;
    }

    @Tool(
            name = "queryOpportunities",
            description = "Query dealer opportunity data for funnel, conversion, in-flight pipeline, and lead-source analysis."
    )
    public DataQueryResponse queryOpportunities(
            @ToolParam(description = "Optional exact dealer code filter such as BJ001.", required = false)
            String dealerCode,
            @ToolParam(description = "Optional exact city filter such as Beijing.", required = false)
            String city,
            @ToolParam(description = "Optional exact dealer group name filter.", required = false)
            String dealerGroupName,
            @ToolParam(description = "Optional product model filter such as M7 or X5.", required = false)
            String productModel,
            @ToolParam(description = "Optional stage name filter such as Qualified, Negotiation, Won, or Lost.", required = false)
            String stageName,
            @ToolParam(description = "Optional lead source filter such as Website, Campaign, WeChat, or Referral.", required = false)
            String leadSource,
            @ToolParam(description = "Optional start date in ISO format YYYY-MM-DD.", required = false)
            String startDate,
            @ToolParam(description = "Optional end date in ISO format YYYY-MM-DD.", required = false)
            String endDate,
            @ToolParam(description = "Optional raw mode flag. When true, return full matching rows.", required = false)
            Boolean raw
    ) {
        Map<String, String> filters = ToolFilterSupport.newFilters();
        ToolFilterSupport.put(filters, "dealerCode", dealerCode);
        ToolFilterSupport.put(filters, "city", city);
        ToolFilterSupport.put(filters, "dealerGroupName", dealerGroupName);
        ToolFilterSupport.put(filters, "productModel", productModel);
        ToolFilterSupport.put(filters, "stageName", stageName);
        ToolFilterSupport.put(filters, "leadSource", leadSource);
        ToolFilterSupport.put(filters, "startDate", startDate);
        ToolFilterSupport.put(filters, "endDate", endDate);
        ToolFilterSupport.put(filters, "raw", raw);
        return dataQueryService.query("opportunities", filters);
    }
}
