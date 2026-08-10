package com.brand.agentpoc.controller;

import com.brand.agentpoc.dto.request.ReportDraftRequest;
import com.brand.agentpoc.dto.response.ApiResult;
import com.brand.agentpoc.reporting.application.ReportGenerationRequest;
import com.brand.agentpoc.reporting.application.ReportService;
import com.brand.agentpoc.reporting.domain.ReportDraft;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/drafts")
    public ResponseEntity<ApiResult<ReportDraft>> createDraft(
            @Valid @RequestBody ReportDraftRequest request
    ) {
        try {
            ReportDraft draft = reportService.generate(new ReportGenerationRequest(
                    request.reportType(),
                    request.language(),
                    request.scopeType(),
                    request.scopeId(),
                    request.topic()
            ));
            return ResponseEntity.ok(ApiResult.success(draft));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ApiResult.error(400, exception.getMessage()));
        }
    }

    @GetMapping("/drafts")
    public ApiResult<List<ReportDraft>> listDrafts() {
        return ApiResult.success(reportService.list());
    }

    @GetMapping("/drafts/{id}")
    public ResponseEntity<ApiResult<ReportDraft>> getDraft(@PathVariable String id) {
        try {
            return ResponseEntity.ok(ApiResult.success(reportService.require(id)));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ApiResult.error(400, exception.getMessage()));
        } catch (java.util.NoSuchElementException exception) {
            return ResponseEntity.status(404).body(ApiResult.error(404, exception.getMessage()));
        }
    }

    @GetMapping(value = "/drafts/{id}/markdown", produces = "text/markdown;charset=UTF-8")
    public ResponseEntity<byte[]> exportMarkdown(@PathVariable String id) {
        ReportDraft draft = reportService.require(id);
        String filename = "report-" + draft.reportType().wireName() + "-" + draft.id() + ".md";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "markdown", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(draft.markdown().getBytes(StandardCharsets.UTF_8));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResult<Void>> handleNotFound(NoSuchElementException exception) {
        return ResponseEntity.status(404).body(ApiResult.error(404, exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResult<Void>> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ApiResult.error(400, exception.getMessage()));
    }
}
