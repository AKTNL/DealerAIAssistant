package com.brand.agentpoc.ai;

import com.brand.agentpoc.dto.response.DataQueryResponse;
import com.brand.agentpoc.service.DataQueryService;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class TaskTools {

    private final DataQueryService dataQueryService;

    public TaskTools(DataQueryService dataQueryService) {
        this.dataQueryService = dataQueryService;
    }

    @Tool(
            name = "queryTasks",
            description = "Query sales follow-up task data for activity, backlog, overdue, and opportunity follow-up analysis."
    )
    public DataQueryResponse queryTasks(
            @ToolParam(description = "Optional exact dealer code filter such as BJ001.", required = false)
            String dealerCode,
            @ToolParam(description = "Optional exact city filter such as Beijing.", required = false)
            String city,
            @ToolParam(description = "Optional exact dealer group name filter.", required = false)
            String dealerGroupName,
            @ToolParam(description = "Optional opportunity id filter such as OPP-1001.", required = false)
            String opportunityId,
            @ToolParam(description = "Optional task status filter such as Pending, Completed, In Progress, or Overdue.", required = false)
            String status,
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
        ToolFilterSupport.put(filters, "opportunityId", opportunityId);
        ToolFilterSupport.put(filters, "status", status);
        ToolFilterSupport.put(filters, "startDate", startDate);
        ToolFilterSupport.put(filters, "endDate", endDate);
        ToolFilterSupport.put(filters, "raw", raw);
        return dataQueryService.query("tasks", filters);
    }
}
