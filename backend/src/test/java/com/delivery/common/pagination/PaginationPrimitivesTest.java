package com.delivery.common.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.delivery.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

/** LAC-14: limits, cursor encoding and the capped-list contract shared by every list endpoint. */
class PaginationPrimitivesTest {

    @Test
    void cursorRoundTripsAndIsOpaqueUrlSafe() {
        KeysetCursor cursor = new KeysetCursor(Instant.parse("2026-10-29T10:15:30.123456Z"), UUID.randomUUID());

        String encoded = cursor.encode();

        assertThat(encoded).matches("[A-Za-z0-9_-]+");
        assertThat(KeysetCursor.decodeOrStart(encoded)).isEqualTo(cursor);
    }

    @Test
    void missingCursorMeansFirstPage() {
        assertThat(KeysetCursor.decodeOrStart(null)).isEqualTo(KeysetCursor.START);
        assertThat(KeysetCursor.decodeOrStart("  ")).isEqualTo(KeysetCursor.START);
    }

    @Test
    void malformedCursorIsA400() {
        String noSeparator = Base64.getUrlEncoder().encodeToString("abc".getBytes(StandardCharsets.UTF_8));
        String badInstant = Base64.getUrlEncoder().encodeToString(("yesterday|" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
        String badUuid = Base64.getUrlEncoder().encodeToString("2026-01-01T00:00:00Z|nope".getBytes(StandardCharsets.UTF_8));
        for (String bad : List.of("%%%not-base64", noSeparator, badInstant, badUuid)) {
            assertThatThrownBy(() -> KeysetCursor.decodeOrStart(bad))
                    .isInstanceOfSatisfying(BusinessException.class, e -> {
                        assertThat(e.getCode()).isEqualTo("invalid_cursor");
                        assertThat(e.getStatus().value()).isEqualTo(400);
                    });
        }
    }

    @Test
    void limitsAreClamped() {
        assertThat(PageLimits.cursorLimit(0)).isEqualTo(1);
        assertThat(PageLimits.cursorLimit(-5)).isEqualTo(1);
        assertThat(PageLimits.cursorLimit(10_000)).isEqualTo(200);
        Pageable page = PageLimits.page(-3, 10_000);
        assertThat(page.getPageNumber()).isZero();
        assertThat(page.getPageSize()).isEqualTo(100);
        assertThat(page.getSort()).isEqualTo(PageLimits.NEWEST_FIRST);
        assertThat(PageLimits.legacyCap().getPageSize()).isEqualTo(200);
        assertThat(PageLimits.keysetProbe(50).getPageSize()).isEqualTo(51);
    }

    @Test
    void cursorPageDropsTheProbeRowAndPointsAtTheLastReturnedRow() {
        List<KeysetCursor> rows = IntStream.range(0, 4)
                .mapToObj(i -> new KeysetCursor(Instant.parse("2026-10-29T10:00:00Z").minusSeconds(i), UUID.randomUUID()))
                .toList();

        CursorPage<String> more = CursorPage.fromProbe(rows, 3, r -> r, list -> list.stream().map(r -> r.id().toString()).toList());
        CursorPage<String> last = CursorPage.fromProbe(rows.subList(0, 2), 3, r -> r, list -> list.stream().map(r -> r.id().toString()).toList());

        assertThat(more.items()).hasSize(3);
        assertThat(KeysetCursor.decodeOrStart(more.nextCursor())).isEqualTo(rows.get(2));
        assertThat(last.items()).hasSize(2);
        assertThat(last.nextCursor()).isNull();
    }

    @Test
    void cappedListAddsTheTruncatedHeaderOnlyWhenMoreRowsExist() {
        CappedList<Integer> capped = CappedList.of(new PageImpl<>(List.of(1, 2), PageRequest.of(0, 2), 3), i -> i);
        CappedList<Integer> complete = CappedList.of(new PageImpl<>(List.of(1, 2), PageRequest.of(0, 2), 2), i -> i);

        ResponseEntity<List<Integer>> cappedResponse = capped.toResponse();
        ResponseEntity<List<Integer>> completeResponse = complete.toResponse();

        assertThat(cappedResponse.getBody()).containsExactly(1, 2);
        assertThat(cappedResponse.getHeaders().getFirst(CappedList.TRUNCATED_HEADER)).isEqualTo("true");
        assertThat(completeResponse.getHeaders().containsKey(CappedList.TRUNCATED_HEADER)).isFalse();
    }
}
