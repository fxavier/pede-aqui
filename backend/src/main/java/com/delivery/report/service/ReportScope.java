package com.delivery.report.service;

import java.util.UUID;

/** Resolved report scope: mandatory tenant plus optional (or VENDOR_ADMIN-forced) vendor filter. */
public record ReportScope(UUID tenantId, UUID vendorId) {
}
