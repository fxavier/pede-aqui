package com.delivery.common.pagination;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Offset page response {@code { content, page, size, totalElements }} (same shape as /orders/page and /sales/orders). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {

    /** Maps a Spring Data page to the response shape. */
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
