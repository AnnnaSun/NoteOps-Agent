package com.noteops.agent.service.capture;

import com.noteops.agent.model.capture.CaptureAnalysisResult;
import com.noteops.agent.model.capture.CaptureAnalysisResultValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaptureAnalysisResultValidatorTest {

    @Test
    void acceptsValidStructuredResult() {
        CaptureAnalysisResult result = CaptureAnalysisResultValidator.validate(new CaptureAnalysisResult(
            " Captured title ",
            " Structured summary ",
            List.of(" point-1 ", "point-2"),
            List.of("capture", " ai "),
            null,
            0.78,
            " en ",
            List.of(" warning ")
        ));

        assertThat(result.titleCandidate()).isEqualTo("Captured title");
        assertThat(result.summary()).isEqualTo("Structured summary");
        assertThat(result.keyPoints()).containsExactly("point-1", "point-2");
        assertThat(result.tags()).containsExactly("capture", "ai");
        assertThat(result.language()).isEqualTo("en");
    }

    @Test
    void rejectsEmptyOrOutOfRangeResults() {
        assertThatThrownBy(() -> CaptureAnalysisResultValidator.validate(new CaptureAnalysisResult(
            "Title",
            "",
            List.of("point-1"),
            List.of(),
            null,
            0.5,
            null,
            List.of()
        ))).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("summary");

        assertThatThrownBy(() -> CaptureAnalysisResultValidator.validate(new CaptureAnalysisResult(
            "Title",
            "Summary",
            List.of("1", "2", "3", "4", "5", "6", "7", "8", "9"),
            List.of(),
            null,
            0.5,
            null,
            List.of()
        ))).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("key_points");

        assertThatThrownBy(() -> CaptureAnalysisResultValidator.validate(new CaptureAnalysisResult(
            "Title",
            "Summary",
            List.of("point-1"),
            List.of(),
            null,
            1.2,
            null,
            List.of()
        ))).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("confidence");
    }
}
