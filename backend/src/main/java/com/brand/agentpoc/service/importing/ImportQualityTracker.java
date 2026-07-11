package com.brand.agentpoc.service.importing;

import com.brand.agentpoc.dto.response.ImportDataStatus;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ImportQualityTracker {

    private final Map<String, MutableSheetStatus> sheets = new LinkedHashMap<>();

    public void processed(String sheet) {
        sheet(sheet).processedRows++;
    }

    public void imported(String sheet) {
        imported(sheet, 1);
    }

    public void imported(String sheet, int count) {
        sheet(sheet).importedRows += Math.max(count, 0);
    }

    public void normalized(String sheet, String reason) {
        MutableSheetStatus status = sheet(sheet);
        status.normalizedFields++;
        status.issue(reason);
    }

    public void skipped(String sheet, String reason) {
        MutableSheetStatus status = sheet(sheet);
        status.skippedRows++;
        status.issue(reason);
    }

    public void issue(String sheet, String reason) {
        sheet(sheet).issue(reason);
    }

    public ImportDataStatus build(String source, boolean fallbackActive, String message) {
        Map<String, ImportDataStatus.SheetStatus> immutableSheets = new LinkedHashMap<>();
        int processedRows = 0;
        int importedRows = 0;
        int normalizedFields = 0;
        int skippedRows = 0;

        for (Map.Entry<String, MutableSheetStatus> entry : sheets.entrySet()) {
            MutableSheetStatus value = entry.getValue();
            processedRows += value.processedRows;
            importedRows += value.importedRows;
            normalizedFields += value.normalizedFields;
            skippedRows += value.skippedRows;
            immutableSheets.put(entry.getKey(), value.toStatus());
        }

        return new ImportDataStatus(
                source,
                fallbackActive,
                message,
                new ImportDataStatus.Totals(processedRows, importedRows, normalizedFields, skippedRows),
                immutableSheets
        );
    }

    private MutableSheetStatus sheet(String sheet) {
        return sheets.computeIfAbsent(sheet, ignored -> new MutableSheetStatus());
    }

    private static final class MutableSheetStatus {
        private int processedRows;
        private int importedRows;
        private int normalizedFields;
        private int skippedRows;
        private final Map<String, Integer> issues = new LinkedHashMap<>();

        private void issue(String reason) {
            String key = reason == null || reason.isBlank() ? "unspecified" : reason;
            issues.merge(key, 1, Integer::sum);
        }

        private ImportDataStatus.SheetStatus toStatus() {
            return new ImportDataStatus.SheetStatus(
                    processedRows,
                    importedRows,
                    normalizedFields,
                    skippedRows,
                    issues
            );
        }
    }
}
