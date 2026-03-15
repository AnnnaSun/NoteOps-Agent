package com.noteops.agent.api.note;

import com.noteops.agent.api.ApiEnvelope;
import com.noteops.agent.application.note.NoteQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notes")
public class NoteController {

    private final NoteQueryService noteQueryService;

    public NoteController(NoteQueryService noteQueryService) {
        this.noteQueryService = noteQueryService;
    }

    @GetMapping
    public ApiEnvelope<List<NoteSummaryResponse>> list(@RequestParam("user_id") String userId) {
        List<NoteSummaryResponse> notes = noteQueryService.list(userId)
            .stream()
            .map(NoteSummaryResponse::from)
            .toList();
        return ApiEnvelope.success(null, notes);
    }

    @GetMapping("/{id}")
    public ApiEnvelope<NoteDetailResponse> get(@PathVariable String id, @RequestParam("user_id") String userId) {
        NoteQueryService.NoteDetailView noteView = noteQueryService.get(id, userId);
        return ApiEnvelope.success(null, NoteDetailResponse.from(noteView));
    }
}
