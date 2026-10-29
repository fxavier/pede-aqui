package com.delivery.common.pagination;

import java.util.List;
import java.util.function.Function;

/** Keyset page response {@code { items, nextCursor }}; {@code nextCursor} is null on the last page (LAC-14). */
public record CursorPage<T>(List<T> items, String nextCursor) {

    /**
     * Builds a page from rows fetched with {@link PageLimits#keysetProbe(int)} ({@code limit + 1} rows, already
     * in keyset order): the extra row is dropped and signals that a next cursor exists.
     */
    public static <E, T> CursorPage<T> fromProbe(List<E> probe, int limit, Function<E, KeysetCursor> key, Function<List<E>, List<T>> mapper) {
        boolean hasMore = probe.size() > limit;
        List<E> rows = hasMore ? probe.subList(0, limit) : probe;
        String next = hasMore ? key.apply(rows.get(rows.size() - 1)).encode() : null;
        return new CursorPage<>(mapper.apply(rows), next);
    }
}
