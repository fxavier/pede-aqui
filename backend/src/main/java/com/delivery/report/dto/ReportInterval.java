package com.delivery.report.dto;

import com.delivery.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** Supported time-series bucketing intervals, mapped onto Postgres date_trunc units. */
public enum ReportInterval {
    DAY("day"),
    WEEK("week"),
    MONTH("month");

    private final String param;

    ReportInterval(String param) {
        this.param = param;
    }

    /** The lowercase API/query value; also the date_trunc unit (always bound as a parameter, never spliced). */
    public String param() {
        return param;
    }

    /** Parses the lowercase query-string value, rejecting anything outside the contract enum. */
    public static ReportInterval fromParam(String value) {
        for (ReportInterval interval : values()) {
            if (interval.param.equalsIgnoreCase(value)) {
                return interval;
            }
        }
        throw new BusinessException("invalid_interval", "interval must be one of: day, week, month", HttpStatus.BAD_REQUEST);
    }
}
