package com.noteops.agent.api.review;

import com.noteops.agent.api.ApiEnvelope;
import com.noteops.agent.application.review.ReviewApplicationService;
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

    @GetMapping("/today")
    public ApiEnvelope<List<ReviewTodayItemResponse>> today(@RequestParam("user_id") String userId) {
        List<ReviewTodayItemResponse> items = reviewApplicationService.listToday(userId)
            .stream()
            .map(ReviewTodayItemResponse::from)
            .toList();
        return ApiEnvelope.success(null, items);
    }

    @PostMapping("/{reviewItemId}/complete")
    public ApiEnvelope<ReviewCompletionResponse> complete(@PathVariable String reviewItemId,
                                                          @RequestBody CompleteReviewRequest request) {
        ReviewApplicationService.ReviewCompletionView review = reviewApplicationService.complete(
            reviewItemId,
            new ReviewApplicationService.CompleteReviewCommand(
                request.userId(),
                request.completionStatus(),
                request.completionReason()
            )
        );
        return ApiEnvelope.success(null, ReviewCompletionResponse.from(review));
    }
}
