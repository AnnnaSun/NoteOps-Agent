package com.noteops.agent.controller.review;

import com.noteops.agent.dto.review.CompleteReviewRequest;
import com.noteops.agent.dto.review.ReviewCompletionResponse;
import com.noteops.agent.dto.review.ReviewTodayItemResponse;

import com.noteops.agent.dto.ApiEnvelope;
import com.noteops.agent.service.review.ReviewApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final ReviewApplicationService reviewApplicationService;

    public ReviewController(ReviewApplicationService reviewApplicationService) {
        this.reviewApplicationService = reviewApplicationService;
    }

    // 查询今日 review 清单，供 Review 页面使用。
    @GetMapping("/today")
    public ApiEnvelope<List<ReviewTodayItemResponse>> today(@RequestParam("user_id") String userId) {
        List<ReviewTodayItemResponse> items = reviewApplicationService.listToday(userId)
            .stream()
            .map(ReviewTodayItemResponse::from)
            .toList();
        return ApiEnvelope.success(null, items);
    }

    // 完成一次 review，并返回完成后的状态。
    @PostMapping("/{reviewItemId}/complete")
    public ApiEnvelope<ReviewCompletionResponse> complete(@PathVariable String reviewItemId,
                                                          @RequestBody CompleteReviewRequest request) {
        ReviewApplicationService.ReviewCompletionView review = reviewApplicationService.complete(
            reviewItemId,
            new ReviewApplicationService.CompleteReviewCommand(
                request.userId(),
                request.completionStatus(),
                request.completionReason(),
                request.selfRecallResult(),
                request.note()
            )
        );
        return ApiEnvelope.success(null, ReviewCompletionResponse.from(review));
    }
}
