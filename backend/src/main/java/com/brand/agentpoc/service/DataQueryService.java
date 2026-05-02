package com.brand.agentpoc.service;

import com.brand.agentpoc.dto.response.DataQueryResponse;
import java.util.Collections;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DataQueryService {

    public DataQueryResponse query(String dataset, Map<String, String> filters) {
        return new DataQueryResponse(dataset, filters, 0, Collections.emptyList());
    }
}

