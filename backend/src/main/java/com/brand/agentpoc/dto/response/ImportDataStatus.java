package com.brand.agentpoc.dto.response;

import java.util.LinkedHashMap;
import java.util.Map;

public record ImportDataStatus(
        String source,
        boolean fallbackActive,
        String message,
        Totals totals,
        Map<String, SheetStatus> sheets
) {

    public ImportDataStatus {
        source = source == null ? "pending" : source;
        message = message == null ? "" : message;
        totals = totals == null ? new Totals(0, 0, 0, 0) : totals;
        sheets = sheets == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(sheets));
    }

    public static ImportDataStatus pending() {
        return new ImportDataStatus("pending", false, "Data import has not completed.", null, null);
    }

    public record Totals(int processedRows, int importedRows, int normalizedFields, int skippedRows) {
    }

    public record SheetStatus(
            int processedRows,
            int importedRows,
            int normalizedFields,
            int skippedRows,
            Map<String, Integer> issues
    ) {

        public SheetStatus {
            issues = issues == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(issues));
        }
    }
}
