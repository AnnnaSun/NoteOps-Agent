package com.noteops.agent.controller.note;

import com.noteops.agent.dto.note.NoteDetailResponse;
import com.noteops.agent.dto.note.NoteSummaryResponse;

import com.noteops.agent.dto.ApiEnvelope;
import com.noteops.agent.service.note.NoteQueryService;
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

    // 查询用户的 Note 列表，供列表页和聚合页复用。
    @GetMapping
    public ApiEnvelope<List<NoteSummaryResponse>> list(@RequestParam("user_id") String userId) {
        List<NoteSummaryResponse> notes = noteQueryService.list(userId)
            .stream()
            .map(NoteSummaryResponse::from)
            .toList();
        return ApiEnvelope.success(null, notes);
    }

    // 查询 Note 详情，供详情页和下游编排使用。
    @GetMapping("/{id}")
    public ApiEnvelope<NoteDetailResponse> get(@PathVariable String id, @RequestParam("user_id") String userId) {
        NoteQueryService.NoteDetailView noteView = noteQueryService.get(id, userId);
        return ApiEnvelope.success(null, NoteDetailResponse.from(noteView));
    }
}
