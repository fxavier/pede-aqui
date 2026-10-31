package com.delivery.privacy.controller;

import com.delivery.privacy.dto.PersonalDataExport;
import com.delivery.privacy.service.DataExportService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Right of access (LAC-63, W7-C): the caller's personal data as a JSON attachment. Rate-limited (group data-export), audited. */
@RestController
@RequestMapping("/api/v1/me/data-export")
@PreAuthorize("isAuthenticated()")
public class DataExportController {
    private final DataExportService service;

    public DataExportController(DataExportService service) {
        this.service = service;
    }

    @Operation(operationId = "exportMyPersonalData", summary = "Export the caller's personal data (JSON attachment)",
            description = "Profile, notification preferences, saved addresses, favourite stores, order summaries, consents and "
                    + "deletion requests. Limited to a few exports per hour per account (429 rate_limited).")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PersonalDataExport> export() {
        PersonalDataExport export = service.export();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(DataExportService.fileName(export.generatedAt())).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(export);
    }
}
