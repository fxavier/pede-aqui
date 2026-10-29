package com.delivery.common.pagination;

import com.delivery.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Opaque keyset position {@code (createdAt, id)} for newest-first lists (LAC-14). A page returns the rows
 * strictly "older" than the cursor: {@code created_at < c.createdAt OR (created_at = c.createdAt AND id < c.id)}.
 * The cursor carries no tenant: queries always filter by the caller's tenant, so a foreign cursor only moves
 * the position inside the caller's own data.
 */
public record KeysetCursor(Instant createdAt, UUID id) {
    /** Position after the newest possible row: the first page. {@code ffffffff-...} is the greatest Postgres uuid. */
    public static final KeysetCursor START = new KeysetCursor(
            Instant.parse("9999-12-31T23:59:59Z"), new UUID(-1L, -1L));

    /** Encodes the cursor as URL-safe base64 without padding. */
    public String encode() {
        String raw = createdAt + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Decodes a client cursor; null/blank means the first page, anything malformed is a 400 {@code invalid_cursor}. */
    public static KeysetCursor decodeOrStart(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return START;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.indexOf('|');
            if (separator <= 0) {
                throw invalid();
            }
            return new KeysetCursor(Instant.parse(raw.substring(0, separator)), UUID.fromString(raw.substring(separator + 1)));
        } catch (IllegalArgumentException | DateTimeParseException malformed) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException("invalid_cursor", "Cursor is malformed", HttpStatus.BAD_REQUEST);
    }
}
