package com.delivery.report.dto;

import com.delivery.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** The named sales reports available for CSV export. */
public enum SalesReportType {
    SUMMARY("summary"),
    TIMESERIES("timeseries"),
    BY_VENDOR("by-vendor"),
    BY_PRODUCT("by-product"),
    BY_CATEGORY("by-category");

    private final String param;

    SalesReportType(String param) {
        this.param = param;
    }

    public String param() {
        return param;
    }

    /** Parses the report query-string value, rejecting anything outside the contract enum. */
    public static SalesReportType fromParam(String value) {
        for (SalesReportType type : values()) {
            if (type.param.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new BusinessException("invalid_report", "report must be one of: summary, timeseries, by-vendor, by-product, by-category", HttpStatus.BAD_REQUEST);
    }
}
