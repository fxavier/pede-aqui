package com.delivery.common.pagination;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Pagination limits shared by every list endpoint (LAC-14): legacy array endpoints are capped at
 * {@link #LEGACY_LIST_CAP} rows (with {@code X-Truncated: true}), cursor endpoints at {@link #MAX_CURSOR_LIMIT}
 * and offset pages at {@link #MAX_PAGE_SIZE}. Every ordering is newest first with the id as a stable tie-break.
 */
public final class PageLimits {
    public static final int LEGACY_LIST_CAP = 200;
    public static final int DEFAULT_CURSOR_LIMIT = 50;
    public static final int MAX_CURSOR_LIMIT = 200;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    /** Newest first, id descending as tie-break: the same total order the keyset cursors use. */
    public static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private PageLimits() {
    }

    /** Clamps a requested cursor page limit to {@code 1..MAX_CURSOR_LIMIT}. */
    public static int cursorLimit(int requested) {
        return Math.min(Math.max(requested, 1), MAX_CURSOR_LIMIT);
    }

    /** Offset page request with page >= 0 and size clamped to {@code 1..MAX_PAGE_SIZE}, newest first. */
    public static Pageable page(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), NEWEST_FIRST);
    }

    /** First {@link #LEGACY_LIST_CAP} rows, newest first, for the capped legacy array endpoints. */
    public static Pageable legacyCap() {
        return PageRequest.of(0, LEGACY_LIST_CAP, NEWEST_FIRST);
    }

    /** {@code limit + 1} rows (ordering comes from the query): the extra row only tells whether a next page exists. */
    public static Pageable keysetProbe(int limit) {
        return PageRequest.of(0, limit + 1);
    }
}
