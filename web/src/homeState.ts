import type { MainView } from "./navigationState";

export type HomeSummarySnapshotInput = {
  noteCount: number;
  ideaCount: number;
  reviewsTodayCount: number;
  tasksTodayCount: number;
  upcomingReviewsCount: number;
  upcomingTasksCount: number;
};

export type HomeSummarySnapshot = {
  noteCount: number;
  ideaCount: number;
  reviewsTodayCount: number;
  tasksTodayCount: number;
  todayItemCount: number;
  upcomingItemCount: number;
};

export type HomeSummaryCard = {
  key: "notes" | "ideas" | "today" | "upcoming";
  label: string;
  value: number;
  description: string;
};

export type PostCaptureNavigation = {
  nextView: MainView;
  selectedNoteId: string | null;
};

export function createHomeSummarySnapshot(input: HomeSummarySnapshotInput): HomeSummarySnapshot {
  return {
    noteCount: input.noteCount,
    ideaCount: input.ideaCount,
    reviewsTodayCount: input.reviewsTodayCount,
    tasksTodayCount: input.tasksTodayCount,
    todayItemCount: input.reviewsTodayCount + input.tasksTodayCount,
    upcomingItemCount: input.upcomingReviewsCount + input.upcomingTasksCount
  };
}

export function buildHomeSummaryCards(snapshot: HomeSummarySnapshot): HomeSummaryCard[] {
  return [
    {
      key: "notes",
      label: "Notes",
      value: snapshot.noteCount,
      description: "当前解释层可浏览条目"
    },
    {
      key: "ideas",
      label: "Ideas",
      value: snapshot.ideaCount,
      description: "已进入主链的想法条目"
    },
    {
      key: "today",
      label: "Today",
      value: snapshot.todayItemCount,
      description: "今日待处理的复习与任务"
    },
    {
      key: "upcoming",
      label: "Upcoming",
      value: snapshot.upcomingItemCount,
      description: "未来待处理的复习与任务"
    }
  ];
}

export function resolvePostCaptureNavigation(noteId: string | null): PostCaptureNavigation {
  return {
    nextView: "NOTES",
    selectedNoteId: noteId
  };
}
