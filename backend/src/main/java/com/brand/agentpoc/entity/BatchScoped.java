package com.brand.agentpoc.entity;

public interface BatchScoped {

    String LEGACY_BATCH_ID = "legacy-default";

    String getImportBatchId();
}
