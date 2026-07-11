package com.brand.agentpoc.service;

import com.brand.agentpoc.dto.response.ImportDataStatus;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class ImportQualityService {

    private final AtomicReference<ImportDataStatus> latest =
            new AtomicReference<>(ImportDataStatus.pending());

    public ImportDataStatus getLatest() {
        return latest.get();
    }

    public void publish(ImportDataStatus status) {
        latest.set(status);
    }
}
