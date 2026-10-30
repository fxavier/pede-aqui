package com.delivery.common.pagination;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

/**
 * Result of a legacy array endpoint capped at {@link PageLimits#LEGACY_LIST_CAP} (LAC-14). The JSON body stays a
 * plain array; {@link #toResponse()} adds {@code X-Truncated: true} when more rows exist than were returned.
 */
public record CappedList<T>(List<T> items, boolean truncated) {
    public static final String TRUNCATED_HEADER = "X-Truncated";

    /** Builds the capped list from the first page fetched with {@link PageLimits#legacyCap()}. */
    public static <E, T> CappedList<T> of(Page<E> firstPage, Function<E, T> mapper) {
        return new CappedList<>(firstPage.getContent().stream().map(mapper).toList(), firstPage.hasNext());
    }

    /** HTTP 200 with the array body and, when capped, the {@code X-Truncated: true} header. */
    public ResponseEntity<List<T>> toResponse() {
        ResponseEntity.BodyBuilder ok = ResponseEntity.ok();
        if (truncated) {
            ok.header(TRUNCATED_HEADER, "true");
        }
        return ok.body(items);
    }
}
