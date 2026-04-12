package com.noteops.agent.service.trend;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.model.trend.TrendItemStatus;
import com.noteops.agent.model.trend.TrendSourceType;
import com.noteops.agent.repository.trend.TrendItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrendInboxQueryServiceTest {

    @Mock
    private TrendItemRepository trendItemRepository;

    @Test
    void rejectsInvalidUserIdWithTraceIdAndStableErrorCode() {
        TrendInboxQueryService service = new TrendInboxQueryService(trendItemRepository);

        assertThatThrownBy(() -> service.list(new TrendInboxQueryService.InboxQueryCommand(
            "bad-user-id",
            null,
            null,
            "trace-1"
        )))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                org.assertj.core.api.Assertions.assertThat(exception.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                org.assertj.core.api.Assertions.assertThat(exception.errorCode()).isEqualTo("INVALID_USER_ID");
                org.assertj.core.api.Assertions.assertThat(exception.traceId()).isEqualTo("trace-1");
                org.assertj.core.api.Assertions.assertThat(exception.getMessage()).isEqualTo("user_id must be a valid UUID");
            });
    }

    @Test
    void rejectsInvalidStatusWithTraceIdAndStableErrorCode() {
        TrendInboxQueryService service = new TrendInboxQueryService(trendItemRepository);

        assertThatThrownBy(() -> service.list(new TrendInboxQueryService.InboxQueryCommand(
            UUID.randomUUID().toString(),
            "NOT_A_STATUS",
            null,
            "trace-2"
        )))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                org.assertj.core.api.Assertions.assertThat(exception.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                org.assertj.core.api.Assertions.assertThat(exception.errorCode()).isEqualTo("INVALID_TREND_STATUS");
                org.assertj.core.api.Assertions.assertThat(exception.traceId()).isEqualTo("trace-2");
                org.assertj.core.api.Assertions.assertThat(exception.getMessage()).isEqualTo("status must be a valid trend item status");
            });
    }

    @Test
    void rejectsInvalidSourceTypeWithTraceIdAndStableErrorCode() {
        TrendInboxQueryService service = new TrendInboxQueryService(trendItemRepository);

        assertThatThrownBy(() -> service.list(new TrendInboxQueryService.InboxQueryCommand(
            UUID.randomUUID().toString(),
            TrendItemStatus.ANALYZED.name(),
            "NOT_A_SOURCE",
            "trace-3"
        )))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                org.assertj.core.api.Assertions.assertThat(exception.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                org.assertj.core.api.Assertions.assertThat(exception.errorCode()).isEqualTo("INVALID_TREND_SOURCE_TYPE");
                org.assertj.core.api.Assertions.assertThat(exception.traceId()).isEqualTo("trace-3");
                org.assertj.core.api.Assertions.assertThat(exception.getMessage()).isEqualTo("source_type must be a valid trend source type");
            });
    }

    @Test
    void wrapsUnexpectedFailuresWithStableClientMessage() {
        TrendInboxQueryService service = new TrendInboxQueryService(trendItemRepository);
        when(trendItemRepository.findInboxByUserId(any(UUID.class), any(TrendItemStatus.class), any(TrendSourceType.class)))
            .thenThrow(new RuntimeException("db password leaked"));

        assertThatThrownBy(() -> service.list(new TrendInboxQueryService.InboxQueryCommand(
            UUID.randomUUID().toString(),
            TrendItemStatus.ANALYZED.name(),
            TrendSourceType.HN.name(),
            "trace-4"
        )))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                org.assertj.core.api.Assertions.assertThat(exception.httpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                org.assertj.core.api.Assertions.assertThat(exception.errorCode()).isEqualTo("TREND_INBOX_QUERY_FAILED");
                org.assertj.core.api.Assertions.assertThat(exception.traceId()).isEqualTo("trace-4");
                org.assertj.core.api.Assertions.assertThat(exception.getMessage()).isEqualTo("trend inbox query failed");
            });
    }
}
