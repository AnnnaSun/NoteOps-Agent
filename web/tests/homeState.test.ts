import test from "node:test";
import assert from "node:assert/strict";

import {
  buildHomeSummaryCards,
  createHomeSummarySnapshot,
  resolvePostCaptureNavigation
} from "../src/homeState.ts";

test("createHomeSummarySnapshot aggregates today and upcoming counts for Home", () => {
  assert.deepEqual(
    createHomeSummarySnapshot({
      noteCount: 12,
      ideaCount: 5,
      reviewsTodayCount: 3,
      tasksTodayCount: 4,
      upcomingReviewsCount: 6,
      upcomingTasksCount: 2
    }),
    {
      noteCount: 12,
      ideaCount: 5,
      reviewsTodayCount: 3,
      tasksTodayCount: 4,
      todayItemCount: 7,
      upcomingItemCount: 8
    }
  );
});

test("buildHomeSummaryCards exposes concise labels and counts", () => {
  assert.deepEqual(
    buildHomeSummaryCards({
      noteCount: 9,
      ideaCount: 2,
      reviewsTodayCount: 1,
      tasksTodayCount: 3,
      todayItemCount: 4,
      upcomingItemCount: 6
    }),
    [
      { key: "notes", label: "Notes", value: 9, description: "当前解释层可浏览条目" },
      { key: "ideas", label: "Ideas", value: 2, description: "已进入主链的想法条目" },
      { key: "today", label: "Today", value: 4, description: "今日待处理的复习与任务" },
      { key: "upcoming", label: "Upcoming", value: 6, description: "未来待处理的复习与任务" }
    ]
  );
});

test("resolvePostCaptureNavigation always sends success flow to NOTES and opens the new note when present", () => {
  assert.deepEqual(resolvePostCaptureNavigation("note-123"), {
    nextView: "NOTES",
    selectedNoteId: "note-123"
  });
  assert.deepEqual(resolvePostCaptureNavigation(null), {
    nextView: "NOTES",
    selectedNoteId: null
  });
});
