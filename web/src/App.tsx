import { FormEvent, useEffect, useState, useTransition } from "react";
import {
  applyChangeProposal,
  completeReview,
  createSearchChangeProposal,
  createCapture,
  createChangeProposal,
  getWorkspaceToday,
  getWorkspaceUpcoming,
  getNote,
  listChangeProposals,
  listNotes,
  saveSearchEvidence,
  searchNotes,
  rollbackChangeProposal
} from "./api";
import type {
  CaptureResponse,
  ChangeProposal,
  NoteDetail,
  NoteSummary,
  ReviewCompletionPayload,
  ReviewTodayItem,
  SearchEvidenceResult,
  SearchResult,
  TaskItem,
  WorkspaceToday,
  WorkspaceUpcoming
} from "./types";

const DEFAULT_USER_ID = "11111111-1111-1111-1111-111111111111";
const USER_ID_STORAGE_KEY = "noteops-user-id";
const REVIEW_COMPLETION_STATUS_OPTIONS = ["COMPLETED", "PARTIAL", "NOT_STARTED", "ABANDONED"] as const;
const REVIEW_COMPLETION_REASON_OPTIONS = ["TIME_LIMIT", "TOO_HARD", "VAGUE_MEMORY", "DEFERRED"] as const;
const REVIEW_SELF_RECALL_RESULT_OPTIONS = ["GOOD", "VAGUE", "FAILED"] as const;

function getTimezoneOffsetLabel(): string {
  const minutes = -new Date().getTimezoneOffset();
  const sign = minutes >= 0 ? "+" : "-";
  const absoluteMinutes = Math.abs(minutes);
  const hours = String(Math.floor(absoluteMinutes / 60)).padStart(2, "0");
  const mins = String(absoluteMinutes % 60).padStart(2, "0");
  return `${sign}${hours}:${mins}`;
}

function formatDateTime(value: string | null): string {
  if (!value) {
    return "暂无";
  }
  return new Intl.DateTimeFormat("zh-CN", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}

function getReviewMetaTone(value: string): string {
  switch (value) {
    case "RECALL":
      return "tone-cyan";
    case "SCHEDULE":
      return "tone-slate";
    case "PARTIAL":
    case "NOT_STARTED":
      return "tone-amber";
    case "COMPLETED":
      return "tone-green";
    default:
      return "tone-slate";
  }
}

function getTaskMetaTone(value: string): string {
  switch (value) {
    case "SYSTEM":
      return "tone-cyan";
    case "USER":
      return "tone-violet";
    case "DONE":
      return "tone-green";
    case "IN_PROGRESS":
      return "tone-blue";
    case "TODO":
      return "tone-amber";
    default:
      return "tone-slate";
  }
}

function getProposalStatusClass(value: string): string {
  switch (value) {
    case "PENDING":
    case "PENDING_REVIEW":
      return "status-pending";
    case "APPLIED":
      return "status-applied";
    case "ROLLED_BACK":
      return "status-rolled_back";
    default:
      return `status-${value.toLowerCase()}`;
  }
}

function canApplyProposal(status: string): boolean {
  return status === "PENDING" || status === "PENDING_REVIEW";
}

function getCaptureStatusLabel(status: string): string {
  switch (status) {
    case "RECEIVED":
      return "已接收";
    case "EXTRACTING":
      return "提取中";
    case "ANALYZING":
      return "分析中";
    case "CONSOLIDATING":
      return "写入中";
    case "COMPLETED":
      return "已完成";
    case "FAILED":
      return "失败";
    default:
      return status;
  }
}

function getCaptureFailureReasonLabel(reason: string | null): string {
  switch (reason) {
    case null:
      return "暂无";
    case "EXTRACTION_FAILED":
      return "内容提取失败";
    case "LLM_CALL_FAILED":
      return "AI 调用失败";
    case "LLM_OUTPUT_INVALID":
      return "AI 输出无效";
    case "CONSOLIDATION_FAILED":
      return "写入 Note 失败";
    default:
      return reason;
  }
}

function getSearchAiStatusLabel(status: string): string {
  switch (status) {
    case "COMPLETED":
      return "已增强";
    case "DEGRADED":
      return "已降级";
    case "SKIPPED":
      return "未触发";
    default:
      return status;
  }
}

function getAiEnhancementLabel(isAiEnhanced: boolean): string {
  return isAiEnhanced ? "AI 增强" : "规则回退";
}

function getReviewQueueLabel(value: string): string {
  switch (value) {
    case "RECALL":
      return "回忆补强";
    case "SCHEDULE":
      return "计划复习";
    default:
      return value;
  }
}

function getReviewCompletionStatusLabel(value: string): string {
  switch (value) {
    case "COMPLETED":
      return "已完成";
    case "PARTIAL":
      return "部分完成";
    case "NOT_STARTED":
      return "未开始";
    case "ABANDONED":
      return "已放弃";
    default:
      return value;
  }
}

function getReviewCompletionReasonLabel(value: string): string {
  switch (value) {
    case "TIME_LIMIT":
      return "时间不够";
    case "TOO_HARD":
      return "内容太难";
    case "VAGUE_MEMORY":
      return "记忆模糊";
    case "DEFERRED":
      return "暂缓处理";
    default:
      return value;
  }
}

function getSelfRecallResultLabel(value: string): string {
  switch (value) {
    case "GOOD":
      return "回忆良好";
    case "VAGUE":
      return "回忆模糊";
    case "FAILED":
      return "回忆失败";
    default:
      return value;
  }
}

function getTaskSourceLabel(value: string): string {
  switch (value) {
    case "SYSTEM":
      return "系统任务";
    case "USER":
      return "用户任务";
    default:
      return value;
  }
}

function getTaskStatusLabel(value: string): string {
  switch (value) {
    case "TODO":
      return "待开始";
    case "IN_PROGRESS":
      return "进行中";
    case "DONE":
      return "已完成";
    case "SKIPPED":
      return "已跳过";
    case "CANCELLED":
      return "已取消";
    default:
      return value;
  }
}

function getProposalStatusLabel(value: string): string {
  switch (value) {
    case "PENDING":
    case "PENDING_REVIEW":
      return "待审核";
    case "APPLIED":
      return "已应用";
    case "ROLLED_BACK":
      return "已回滚";
    default:
      return value;
  }
}

function getProposalTargetLayerLabel(value: string): string {
  switch (value) {
    case "INTERPRETATION":
      return "解释层";
    case "METADATA":
      return "元数据层";
    case "RELATION":
      return "关系层";
    default:
      return value;
  }
}

function getProposalRiskLabel(value: string): string {
  switch (value) {
    case "LOW":
      return "低风险";
    case "MEDIUM":
      return "中风险";
    case "HIGH":
      return "高风险";
    default:
      return value;
  }
}

function readStoredUserId(): string {
  if (typeof window === "undefined") {
    return DEFAULT_USER_ID;
  }
  return window.localStorage.getItem(USER_ID_STORAGE_KEY) ?? DEFAULT_USER_ID;
}

type WorkspaceSnapshot = {
  today: WorkspaceToday;
  upcoming: WorkspaceUpcoming;
};

type UpcomingReviewGroup = {
  noteId: string;
  title: string;
  currentSummary: string;
  reviews: ReviewTodayItem[];
};

async function fetchWorkspaceSnapshot(userId: string): Promise<WorkspaceSnapshot> {
  const timezoneOffset = getTimezoneOffsetLabel();
  const [today, upcoming] = await Promise.all([
    getWorkspaceToday(userId, timezoneOffset),
    getWorkspaceUpcoming(userId, timezoneOffset)
  ]);
  return { today, upcoming };
}

function getReviewTimingLabel(review: ReviewTodayItem): string {
  if (review.next_review_at) {
    return `下次 ${formatDateTime(review.next_review_at)}`;
  }
  if (review.retry_after_hours > 0) {
    return `${review.retry_after_hours}h 后重试`;
  }
  return "等待重新排期";
}

function getTaskTimingLabel(task: TaskItem): string {
  return task.due_at ? `截止 ${formatDateTime(task.due_at)}` : "未设置截止时间";
}

function getUpcomingReviewQueueDescription(review: ReviewTodayItem): string {
  return review.queue_type === "RECALL"
    ? "短期回忆补强队列，用来快速追踪未掌握内容。"
    : "长期计划复习队列，用来保留正常复习周期。";
}

function groupUpcomingReviewsByNote(reviews: ReviewTodayItem[]): UpcomingReviewGroup[] {
  const groups = new Map<string, UpcomingReviewGroup>();
  for (const review of reviews) {
    const existing = groups.get(review.note_id);
    if (existing) {
      existing.reviews.push(review);
      continue;
    }
    groups.set(review.note_id, {
      noteId: review.note_id,
      title: review.title,
      currentSummary: review.current_summary,
      reviews: [review]
    });
  }
  return Array.from(groups.values());
}

type ReviewFormDraft = {
  completionStatus: string;
  completionReason: string;
  selfRecallResult: string;
  note: string;
};

function isRecallQueue(queueType: string): boolean {
  return queueType === "RECALL";
}

function requiresCompletionReason(completionStatus: string): boolean {
  return completionStatus !== "COMPLETED";
}

function createReviewFormDraft(queueType: string): ReviewFormDraft {
  return {
    completionStatus: "COMPLETED",
    completionReason: "",
    selfRecallResult: isRecallQueue(queueType) ? "GOOD" : "",
    note: ""
  };
}

function normalizeOptionalValue(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}

export default function App() {
  const [userIdInput, setUserIdInput] = useState(readStoredUserId);
  const [activeUserId, setActiveUserId] = useState(readStoredUserId);
  const [notes, setNotes] = useState<NoteSummary[]>([]);
  const [selectedNoteId, setSelectedNoteId] = useState<string | null>(null);
  const [selectedNote, setSelectedNote] = useState<NoteDetail | null>(null);
  const [proposals, setProposals] = useState<ChangeProposal[]>([]);
  const [reviewsToday, setReviewsToday] = useState<ReviewTodayItem[]>([]);
  const [tasksToday, setTasksToday] = useState<TaskItem[]>([]);
  const [upcomingReviews, setUpcomingReviews] = useState<ReviewTodayItem[]>([]);
  const [upcomingTasks, setUpcomingTasks] = useState<TaskItem[]>([]);
  const [captureResult, setCaptureResult] = useState<CaptureResponse | null>(null);
  const [captureInputType, setCaptureInputType] = useState<"TEXT" | "URL">("TEXT");
  const [captureText, setCaptureText] = useState("");
  const [captureSourceUri, setCaptureSourceUri] = useState("");
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResult, setSearchResult] = useState<SearchResult | null>(null);
  const [hasSearched, setHasSearched] = useState(false);
  const [activeReviewFormId, setActiveReviewFormId] = useState<string | null>(null);
  const [reviewFormDraft, setReviewFormDraft] = useState<ReviewFormDraft | null>(null);
  const [notesError, setNotesError] = useState<string | null>(null);
  const [workspaceError, setWorkspaceError] = useState<string | null>(null);
  const [noteError, setNoteError] = useState<string | null>(null);
  const [proposalError, setProposalError] = useState<string | null>(null);
  const [captureError, setCaptureError] = useState<string | null>(null);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [searchActionMessage, setSearchActionMessage] = useState<string | null>(null);
  const [reviewFormError, setReviewFormError] = useState<string | null>(null);
  const [isBootstrapping, setIsBootstrapping] = useState(true);
  const [isSubmittingCapture, setIsSubmittingCapture] = useState(false);
  const [isSearching, setIsSearching] = useState(false);
  const [isMutatingSearchAction, setIsMutatingSearchAction] = useState(false);
  const [isRefreshingWorkspace, startWorkspaceTransition] = useTransition();
  const [isRefreshingNote, startNoteTransition] = useTransition();
  const [isSubmittingReview, startReviewTransition] = useTransition();
  const [isMutatingProposal, startProposalTransition] = useTransition();
  const isDetailVisible = Boolean(selectedNoteId || noteError || isRefreshingNote);
  const upcomingItemCount = upcomingReviews.length + upcomingTasks.length;
  const upcomingReviewGroups = groupUpcomingReviewsByNote(upcomingReviews);
  const exactMatches = searchResult?.exact_matches ?? [];
  const relatedMatches = searchResult?.related_matches ?? [];
  const externalSupplements = searchResult?.external_supplements ?? [];
  const searchAiEnhancementStatus = searchResult?.ai_enhancement_status ?? "SKIPPED";

  function applyWorkspaceSnapshot(snapshot: WorkspaceSnapshot) {
    setReviewsToday(snapshot.today.today_reviews);
    setTasksToday(snapshot.today.today_tasks);
    setUpcomingReviews(snapshot.upcoming.upcoming_reviews);
    setUpcomingTasks(snapshot.upcoming.upcoming_tasks);
  }

  function resetSearchState() {
    setSearchQuery("");
    setSearchResult(null);
    setSearchError(null);
    setSearchActionMessage(null);
    setHasSearched(false);
  }

  function closeReviewForm() {
    setActiveReviewFormId(null);
    setReviewFormDraft(null);
    setReviewFormError(null);
  }

  useEffect(() => {
    if (typeof window !== "undefined") {
      window.localStorage.setItem(USER_ID_STORAGE_KEY, activeUserId);
    }
  }, [activeUserId]);

  useEffect(() => {
    let cancelled = false;

    async function bootstrap() {
      setIsBootstrapping(true);
      setNotesError(null);
      setWorkspaceError(null);
      try {
        const [noteItems, workspaceSnapshot] = await Promise.all([
          listNotes(activeUserId),
          fetchWorkspaceSnapshot(activeUserId)
        ]);
        if (cancelled) {
          return;
        }
        setNotes(noteItems);
        applyWorkspaceSnapshot(workspaceSnapshot);
        setSelectedNoteId(noteItems[0]?.id ?? null);
      } catch (error) {
        if (cancelled) {
          return;
        }
        const message = error instanceof Error ? error.message : "加载初始数据失败";
        setNotesError(message);
        setWorkspaceError(message);
        setNotes([]);
        setReviewsToday([]);
        setTasksToday([]);
        setUpcomingReviews([]);
        setUpcomingTasks([]);
        setSelectedNoteId(null);
      } finally {
        if (!cancelled) {
          setIsBootstrapping(false);
        }
      }
    }

    void bootstrap();
    return () => {
      cancelled = true;
    };
  }, [activeUserId]);

  useEffect(() => {
    if (!selectedNoteId) {
      setSelectedNote(null);
      setProposals([]);
      setNoteError(null);
      setProposalError(null);
      return;
    }

    let cancelled = false;
    startNoteTransition(() => {
      void (async () => {
        setNoteError(null);
        setProposalError(null);
        try {
          const [note, proposalItems] = await Promise.all([
            getNote(selectedNoteId, activeUserId),
            listChangeProposals(selectedNoteId, activeUserId)
          ]);
          if (cancelled) {
            return;
          }
          setSelectedNote(note);
          setProposals(proposalItems);
        } catch (error) {
          if (cancelled) {
            return;
          }
          const message = error instanceof Error ? error.message : "加载笔记详情失败";
          setNoteError(message);
          setProposalError(message);
          setSelectedNote(null);
          setProposals([]);
        }
      })();
    });

    return () => {
      cancelled = true;
    };
  }, [activeUserId, selectedNoteId]);

  async function refreshNotes(preferredNoteId?: string) {
    const noteItems = await listNotes(activeUserId);
    setNotes(noteItems);
    const nextSelectedId = preferredNoteId ?? selectedNoteId ?? noteItems[0]?.id ?? null;
    setSelectedNoteId(nextSelectedId);
    if (nextSelectedId) {
      const [note, proposalItems] = await Promise.all([
        getNote(nextSelectedId, activeUserId),
        listChangeProposals(nextSelectedId, activeUserId)
      ]);
      setSelectedNote(note);
      setProposals(proposalItems);
    } else {
      setSelectedNote(null);
      setProposals([]);
    }
  }

  function handleUserIdApply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextUserId = userIdInput.trim();
    if (!nextUserId) {
      return;
    }
    setCaptureResult(null);
    resetSearchState();
    closeReviewForm();
    setActiveUserId(nextUserId);
  }

  function handleCaptureSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void (async () => {
      setIsSubmittingCapture(true);
      setCaptureError(null);
      try {
        const result = await createCapture({
          userId: activeUserId,
          sourceType: captureInputType,
          rawText: captureInputType === "TEXT" ? captureText : undefined,
          sourceUrl: captureInputType === "URL" ? captureSourceUri : undefined
        });
        setCaptureResult(result);
        setCaptureText("");
        setCaptureSourceUri("");
        await refreshNotes(result.note_id ?? undefined);
        startWorkspaceTransition(() => {
          void (async () => {
            try {
              const workspaceSnapshot = await fetchWorkspaceSnapshot(activeUserId);
              applyWorkspaceSnapshot(workspaceSnapshot);
              setWorkspaceError(null);
            } catch (error) {
              setWorkspaceError(error instanceof Error ? error.message : "刷新工作台失败");
            }
          })();
        });
      } catch (error) {
        setCaptureError(error instanceof Error ? error.message : "提交采集失败");
      } finally {
        setIsSubmittingCapture(false);
      }
    })();
  }

  function handleProposalCreate() {
    if (!selectedNoteId) {
      return;
    }
    startProposalTransition(() => {
      void (async () => {
        setProposalError(null);
        try {
          await createChangeProposal(selectedNoteId, activeUserId);
          const proposalItems = await listChangeProposals(selectedNoteId, activeUserId);
          setProposals(proposalItems);
        } catch (error) {
          setProposalError(error instanceof Error ? error.message : "生成更新建议失败");
        }
      })();
    });
  }

  function handleProposalApply(proposalId: string) {
    if (!selectedNoteId) {
      return;
    }
    startProposalTransition(() => {
      void (async () => {
        setProposalError(null);
        try {
          await applyChangeProposal(selectedNoteId, proposalId, activeUserId);
          await refreshNotes(selectedNoteId);
        } catch (error) {
          setProposalError(error instanceof Error ? error.message : "应用更新建议失败");
        }
      })();
    });
  }

  function handleProposalRollback(proposalId: string) {
    startProposalTransition(() => {
      void (async () => {
        setProposalError(null);
        try {
          await rollbackChangeProposal(proposalId, activeUserId);
          await refreshNotes(selectedNoteId ?? undefined);
        } catch (error) {
          setProposalError(error instanceof Error ? error.message : "回滚更新建议失败");
        }
      })();
    });
  }

  function handleWorkspaceRefresh() {
    startWorkspaceTransition(() => {
      void (async () => {
        setWorkspaceError(null);
        try {
          const workspaceSnapshot = await fetchWorkspaceSnapshot(activeUserId);
          applyWorkspaceSnapshot(workspaceSnapshot);
        } catch (error) {
          setWorkspaceError(error instanceof Error ? error.message : "刷新工作台失败");
        }
      })();
    });
  }

  function handleSearchSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const query = searchQuery.trim();
    if (!query) {
      return;
    }
    setHasSearched(true);
    void (async () => {
      setIsSearching(true);
      setSearchError(null);
      setSearchActionMessage(null);
      try {
        const result = await searchNotes(activeUserId, query);
        setSearchResult(result);
      } catch (error) {
        setSearchResult(null);
        setSearchError(error instanceof Error ? error.message : "执行搜索失败");
      } finally {
        setIsSearching(false);
      }
    })();
  }

  function handleSearchEvidenceSave(item: SearchResult["external_supplements"][number]) {
    if (!selectedNoteId || !searchResult) {
      setSearchActionMessage("请先打开一个目标笔记，再保存证据或生成更新建议。");
      return;
    }
    void (async () => {
      setIsMutatingSearchAction(true);
      setSearchError(null);
      setSearchActionMessage(null);
      try {
        const result: SearchEvidenceResult = await saveSearchEvidence(selectedNoteId, {
          userId: activeUserId,
          query: searchResult.query,
          sourceName: item.source_name,
          sourceUri: item.source_uri,
          summary: item.summary,
          keywords: item.keywords,
          relationLabel: item.relation_label,
          relationTags: item.relation_tags,
          summarySnippet: item.summary_snippet
        });
        await refreshNotes(selectedNoteId);
        setSearchActionMessage(`已写入 EVIDENCE：${result.content_id}`);
      } catch (error) {
        setSearchError(error instanceof Error ? error.message : "保存搜索证据失败");
      } finally {
        setIsMutatingSearchAction(false);
      }
    })();
  }

  function handleSearchProposalCreate(item: SearchResult["external_supplements"][number]) {
    if (!selectedNoteId || !searchResult) {
      setSearchActionMessage("请先打开一个目标笔记，再保存证据或生成更新建议。");
      return;
    }
    // 只有 AI 成功增强过的 external supplement 才允许进入 proposal 治理链路。
    if (!item.is_ai_enhanced) {
      setSearchActionMessage("规则回退的外部补充仅允许保存为证据，不允许直接生成更新建议。");
      return;
    }
    void (async () => {
      setIsMutatingSearchAction(true);
      setSearchError(null);
      setSearchActionMessage(null);
      try {
        await createSearchChangeProposal(selectedNoteId, {
          userId: activeUserId,
          query: searchResult.query,
          sourceName: item.source_name,
          sourceUri: item.source_uri,
          summary: item.summary,
          keywords: item.keywords,
          relationLabel: item.relation_label,
          relationTags: item.relation_tags,
          summarySnippet: item.summary_snippet
        });
        await refreshNotes(selectedNoteId);
        setSearchActionMessage("已生成搜索更新建议。");
      } catch (error) {
        setSearchError(error instanceof Error ? error.message : "生成搜索更新建议失败");
      } finally {
        setIsMutatingSearchAction(false);
      }
    })();
  }

  function handleReviewFormToggle(review: ReviewTodayItem) {
    if (activeReviewFormId === review.id) {
      closeReviewForm();
      return;
    }
    setActiveReviewFormId(review.id);
    setReviewFormDraft(createReviewFormDraft(review.queue_type));
    setReviewFormError(null);
  }

  function handleReviewDraftChange(field: keyof ReviewFormDraft, value: string) {
    setReviewFormDraft((current) => {
      if (!current) {
        return current;
      }
      if (field === "completionStatus") {
        return {
          ...current,
          completionStatus: value,
          completionReason: value === "COMPLETED" ? "" : current.completionReason
        };
      }
      return {
        ...current,
        [field]: value
      };
    });
    setReviewFormError(null);
  }

  function isReviewFormValid(review: ReviewTodayItem, draft: ReviewFormDraft | null): boolean {
    if (!draft) {
      return false;
    }
    if (requiresCompletionReason(draft.completionStatus) && !draft.completionReason.trim()) {
      return false;
    }
    if (isRecallQueue(review.queue_type) && !draft.selfRecallResult.trim()) {
      return false;
    }
    return true;
  }

  function buildReviewCompletionPayload(review: ReviewTodayItem, draft: ReviewFormDraft): ReviewCompletionPayload {
    const payload: ReviewCompletionPayload = {
      user_id: activeUserId,
      completion_status: draft.completionStatus
    };
    if (requiresCompletionReason(draft.completionStatus)) {
      payload.completion_reason = normalizeOptionalValue(draft.completionReason);
    }
    if (isRecallQueue(review.queue_type)) {
      payload.self_recall_result = normalizeOptionalValue(draft.selfRecallResult);
      const note = normalizeOptionalValue(draft.note);
      if (note) {
        payload.note = note;
      }
    }
    return payload;
  }

  function handleReviewSubmit(event: FormEvent<HTMLFormElement>, review: ReviewTodayItem) {
    event.preventDefault();
    if (!reviewFormDraft || !isReviewFormValid(review, reviewFormDraft)) {
      return;
    }
    startReviewTransition(() => {
      void (async () => {
        setReviewFormError(null);
        try {
          await completeReview(review.id, buildReviewCompletionPayload(review, reviewFormDraft));
          const workspaceSnapshot = await fetchWorkspaceSnapshot(activeUserId);
          applyWorkspaceSnapshot(workspaceSnapshot);
          setWorkspaceError(null);
          closeReviewForm();
        } catch (error) {
          setReviewFormError(error instanceof Error ? error.message : "提交复习结果失败");
        }
      })();
    });
  }

  return (
    <main className="app-shell">
      <section className="hero-panel">
        <div>
          <p className="eyebrow">Phase 3 前置补丁 / AI 采集</p>
          <h1>采集链路已接上最小真实 AI 主链路。</h1>
          <p className="hero-copy">
            当前单页保留既有笔记、更新建议、搜索、复习、工作台页面，并把采集升级为
            TEXT / URL 提取、结构化 AI 分析与默认新建笔记的最小可演示闭环。
          </p>
        </div>
        <form className="user-form" onSubmit={handleUserIdApply}>
          <label htmlFor="user-id">当前 user_id</label>
          <input
            id="user-id"
            value={userIdInput}
            onChange={(event) => setUserIdInput(event.target.value)}
            spellCheck={false}
          />
          <button type="submit">切换上下文</button>
        </form>
      </section>

      <section className="workspace-overview" aria-label="工作台概览">
        <article className="overview-card">
          <span className="overview-label">笔记</span>
          <strong>{notes.length}</strong>
          <p>当前解释层可浏览条目</p>
        </article>
        <article className="overview-card">
          <span className="overview-label">今日复习</span>
          <strong>{reviewsToday.length}</strong>
          <p>今日复习项</p>
        </article>
        <article className="overview-card">
          <span className="overview-label">今日任务</span>
          <strong>{tasksToday.length}</strong>
          <p>待办与跟进任务</p>
        </article>
        <article className="overview-card overview-card-wide">
          <span className="overview-label">后续队列</span>
          <strong>{upcomingItemCount}</strong>
          <p>未来待处理的复习与任务</p>
        </article>
      </section>

      <section className={`workspace-grid ${isDetailVisible ? "detail-open" : "detail-closed"}`}>
        <div className="column-stack">
          <article className="panel">
              <div className="panel-heading">
                <div>
                  <p className="panel-kicker">采集</p>
                  <h2>提交一条新输入并触发 AI 分析</h2>
                </div>
              </div>
            <form className="capture-form" onSubmit={handleCaptureSubmit}>
              <div className="segmented-control" role="tablist" aria-label="采集输入类型">
                <button
                  type="button"
                  className={captureInputType === "TEXT" ? "active" : ""}
                  onClick={() => setCaptureInputType("TEXT")}
                >
                  TEXT
                </button>
                <button
                  type="button"
                  className={captureInputType === "URL" ? "active" : ""}
                  onClick={() => setCaptureInputType("URL")}
                >
                  URL
                </button>
              </div>
              {captureInputType === "URL" ? (
                <input
                  placeholder="https://example.com/article"
                  value={captureSourceUri}
                  onChange={(event) => setCaptureSourceUri(event.target.value)}
                  spellCheck={false}
                />
              ) : null}
              {captureInputType === "TEXT" ? (
                <textarea
                  placeholder="输入要落库的正文内容..."
                  value={captureText}
                  onChange={(event) => setCaptureText(event.target.value)}
                />
              ) : null}
              <div className="form-actions">
                <button
                  type="submit"
                  className={isSubmittingCapture ? "is-loading" : ""}
                  disabled={
                    isSubmittingCapture ||
                    (captureInputType === "TEXT" ? !captureText.trim() : !captureSourceUri.trim())
                  }
                >
                  {isSubmittingCapture ? (
                    <>
                      <span className="button-spinner" aria-hidden="true" />
                      AI 分析中...
                    </>
                  ) : (
                    "提交采集"
                  )}
                </button>
                <span className="meta-chip">当前 user_id: {activeUserId}</span>
              </div>
            </form>
            {isSubmittingCapture ? <p className="status-message">正在调用 AI 分析并写入 Note，请稍候。</p> : null}
            {captureError ? <p className="status-message error">{captureError}</p> : null}
            {captureResult ? (
              <div className="status-card">
                <div className="status-card-header">
                  <strong>最近提交：{getCaptureStatusLabel(captureResult.status)}</strong>
                  {captureResult.note_id ? (
                    <button
                      type="button"
                      className="ghost-button"
                      onClick={() => setSelectedNoteId(captureResult.note_id)}
                    >
                      查看新笔记
                    </button>
                  ) : null}
                </div>
                <p>capture_job_id: {captureResult.capture_job_id}</p>
                <p>note_id: {captureResult.note_id ?? "暂无"}</p>
                <p>failure_reason: {getCaptureFailureReasonLabel(captureResult.failure_reason)}</p>
                {captureResult.analysis_preview ? (
                  <>
                    <p>title_candidate: {captureResult.analysis_preview.title_candidate}</p>
                    <p>{captureResult.analysis_preview.summary}</p>
                  </>
                ) : null}
              </div>
            ) : null}
          </article>

          <article className="panel">
            <div className="panel-heading">
              <div>
                <p className="panel-kicker">搜索</p>
                <h2>三分栏结果</h2>
              </div>
              <span className="meta-chip">API: /api/v1/search</span>
            </div>
            <form className="search-form" onSubmit={handleSearchSubmit}>
              <input
                placeholder="输入查询词，例如 测试"
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value)}
                spellCheck={false}
              />
              <div className="form-actions">
                <button type="submit" className={isSearching ? "is-loading" : ""} disabled={isSearching || !searchQuery.trim()}>
                  {isSearching ? (
                    <>
                      <span className="button-spinner" aria-hidden="true" />
                      AI 检索中...
                    </>
                  ) : (
                    "执行搜索"
                  )}
                </button>
                {searchResult ? <span className="meta-chip">查询词：{searchResult.query}</span> : null}
              </div>
            </form>
            {searchError ? <p className="status-message error">{searchError}</p> : null}
            {isSearching ? <p className="status-message">正在执行检索并等待 AI 增强，请稍候。</p> : null}
            {!hasSearched ? (
              <p className="status-message">输入查询词后执行搜索，结果会按精确匹配 / 相关结果 / 外部补充分栏展示。</p>
            ) : (
              <div className="search-grid">
                <section className="subpanel search-panel-exact">
                  <div className="subpanel-heading">
                    <h3>精确匹配</h3>
                    <span className="meta-chip">{exactMatches.length}</span>
                  </div>
                  <p className="subpanel-description">命中当前笔记解释层或最新内容的直接匹配。</p>
                  {exactMatches.length === 0 ? <p className="status-message">本次查询没有精确匹配结果。</p> : null}
                  {exactMatches.map((match) => (
                    <div key={match.note_id} className="list-row">
                      <div className="list-row-content">
                        <strong>{match.title}</strong>
                        <p>{match.current_summary}</p>
                        {match.current_key_points.length > 0 ? (
                          <div className="note-card-points">
                            {match.current_key_points.slice(0, 3).map((point) => (
                              <span key={`${match.note_id}-${point}`} className="note-card-point">
                                {point}
                              </span>
                            ))}
                          </div>
                        ) : null}
                        <p className="search-result-detail">{match.latest_content}</p>
                        <div className="row-meta row-meta-inline">
                          <span className="tone-slate">{formatDateTime(match.updated_at)}</span>
                        </div>
                        <div className="form-actions">
                          <button type="button" className="ghost-button" onClick={() => setSelectedNoteId(match.note_id)}>
                            打开笔记
                          </button>
                        </div>
                      </div>
                    </div>
                  ))}
                </section>

                <section className="subpanel search-panel-related">
                  <div className="subpanel-heading">
                    <h3>相关结果</h3>
                    <span className="meta-chip">{relatedMatches.length}</span>
                  </div>
                  <p className="subpanel-description">
                    展示与查询词存在内部关联的笔记。标签 `AI 增强` 表示 `is_ai_enhanced=true`，`规则回退` 表示 `false`。
                  </p>
                  {searchAiEnhancementStatus === "DEGRADED" ? (
                    <p className="status-message">本次搜索 AI 增强已降级，相关结果说明可能来自规则回退。</p>
                  ) : null}
                  {relatedMatches.length === 0 ? <p className="status-message">本次查询没有相关结果。</p> : null}
                  {relatedMatches.map((match) => (
                    <div key={match.note_id} className="list-row">
                      <div className="list-row-content">
                        <strong>{match.title}</strong>
                        <p>{match.current_summary}</p>
                        {match.current_key_points.length > 0 ? (
                          <div className="note-card-points">
                            {match.current_key_points.slice(0, 3).map((point) => (
                              <span key={`${match.note_id}-${point}`} className="note-card-point">
                                {point}
                              </span>
                            ))}
                          </div>
                        ) : null}
                        <p className="search-result-detail">{match.latest_content}</p>
                        <div className="row-meta row-meta-inline">
                          <span className="tone-blue">{match.relation_reason}</span>
                          <span className={match.is_ai_enhanced ? "tone-cyan" : "tone-slate"}>
                            {getAiEnhancementLabel(match.is_ai_enhanced)}
                          </span>
                          <span className="tone-slate">{formatDateTime(match.updated_at)}</span>
                        </div>
                        <div className="form-actions">
                          <button type="button" className="ghost-button" onClick={() => setSelectedNoteId(match.note_id)}>
                            打开笔记
                          </button>
                        </div>
                      </div>
                    </div>
                  ))}
                </section>

                <section className="subpanel search-panel-external">
                  <div className="subpanel-heading">
                    <h3>外部补充</h3>
                    <span className="meta-chip">{externalSupplements.length}</span>
                  </div>
                  <p className="subpanel-description">
                    外部补充只可保存为 `EVIDENCE` 或生成 `ChangeProposal`，不会直接覆盖当前笔记解释层。
                  </p>
                  <p className="section-hint">标签 `AI 增强` 表示 `is_ai_enhanced=true`，`规则回退` 表示 `false`。</p>
                  <div className="row-meta row-meta-inline">
                    <span className="meta-chip">
                      目标笔记：{selectedNote ? selectedNote.title : selectedNoteId ?? "未选择"}
                    </span>
                    <span className="meta-chip">AI 状态：{getSearchAiStatusLabel(searchAiEnhancementStatus)}</span>
                  </div>
                  {externalSupplements.length === 0 ? <p className="status-message">本次查询没有外部补充。</p> : null}
                  {searchActionMessage ? <p className="status-message">{searchActionMessage}</p> : null}
                  {!selectedNoteId && externalSupplements.length > 0 ? (
                    <p className="status-message">请先从左侧笔记列表、精确匹配或相关结果中打开一个目标笔记，再保存证据或生成更新建议。</p>
                  ) : null}
                  {searchAiEnhancementStatus === "DEGRADED" ? (
                    <p className="status-message">当前外部补充可能是规则回退结果，不代表 AI 增强链路已成功。</p>
                  ) : null}
                  {externalSupplements.map((item) => (
                    <div key={`${item.source_uri}-${item.summary}`} className="list-row">
                      <div className="list-row-content">
                        <strong>{item.source_name}</strong>
                        <p>{item.summary}</p>
                        <p className="search-result-detail">{item.summary_snippet}</p>
                        <p className="search-result-detail">{item.source_uri}</p>
                        {item.keywords.length > 0 ? (
                          <div className="note-card-points">
                            {item.keywords.map((keyword) => (
                              <span key={`${item.source_uri}-${keyword}`} className="note-card-point">
                                {keyword}
                              </span>
                            ))}
                          </div>
                        ) : null}
                        <div className="row-meta row-meta-inline">
                          <span className="tone-blue">{item.relation_label}</span>
                          <span className={item.is_ai_enhanced ? "tone-cyan" : "tone-slate"}>
                            {getAiEnhancementLabel(item.is_ai_enhanced)}
                          </span>
                          {item.relation_tags.map((tag) => (
                            <span key={`${item.source_uri}-${tag}`} className="tone-cyan">
                              {tag}
                            </span>
                          ))}
                        </div>
                        {!item.is_ai_enhanced ? (
                          <p className="status-message">
                            当前条目为规则回退结果：允许保存为证据，但不允许直接生成更新建议。
                          </p>
                        ) : null}
                        <div className="form-actions">
                          <button
                            type="button"
                            className={`ghost-button ${isMutatingSearchAction ? "is-loading" : ""}`}
                            disabled={!selectedNoteId || isMutatingSearchAction}
                            onClick={() => handleSearchEvidenceSave(item)}
                          >
                            {isMutatingSearchAction ? (
                              <>
                                <span className="button-spinner" aria-hidden="true" />
                                处理中...
                              </>
                            ) : (
                              "保存为证据"
                            )}
                          </button>
                          <button
                            type="button"
                            className={`ghost-button ${isMutatingSearchAction ? "is-loading" : ""}`}
                            disabled={!selectedNoteId || isMutatingSearchAction || !item.is_ai_enhanced}
                            onClick={() => handleSearchProposalCreate(item)}
                          >
                            {isMutatingSearchAction ? (
                              <>
                                <span className="button-spinner" aria-hidden="true" />
                                处理中...
                              </>
                            ) : (
                              "生成更新建议"
                            )}
                          </button>
                        </div>
                      </div>
                    </div>
                  ))}
                </section>
              </div>
            )}
          </article>

          <article className="panel">
            <div className="panel-heading">
              <div>
                <p className="panel-kicker">笔记</p>
                <h2>当前解释层列表</h2>
              </div>
              <span className="meta-chip">{notes.length} 条</span>
            </div>
            <p className="section-hint">点击笔记查看详情，再次点击当前项或右侧关闭按钮可收起详情面板。</p>
            {isBootstrapping ? <p className="status-message">正在加载笔记列表...</p> : null}
            {notesError ? <p className="status-message error">{notesError}</p> : null}
            {!isBootstrapping && !notesError && notes.length === 0 ? (
              <p className="status-message">当前 `user_id` 还没有笔记。先提交一条 TEXT 采集。</p>
            ) : null}
            <div className="note-list">
              {notes.map((note) => (
                <button
                  key={note.id}
                  type="button"
                  className={`note-card ${selectedNoteId === note.id ? "selected" : ""}`}
                  onClick={() => setSelectedNoteId((current) => (current === note.id ? null : note.id))}
                >
                  <span className="note-card-row">
                    <span className="note-card-title">{note.title}</span>
                    <span className="note-card-pill">{selectedNoteId === note.id ? "已打开" : "查看"}</span>
                  </span>
                  <span className="note-card-summary">{note.current_summary}</span>
                  {note.current_key_points.length > 0 ? (
                    <span className="note-card-points">
                      {note.current_key_points.slice(0, 2).map((point) => (
                        <span key={point} className="note-card-point">
                          {point}
                        </span>
                      ))}
                    </span>
                  ) : null}
                  <span className="note-card-meta">更新于 {formatDateTime(note.updated_at)}</span>
                </button>
              ))}
            </div>
          </article>

          <article className="panel">
            <div className="panel-heading">
              <div>
                <p className="panel-kicker">工作台</p>
                <h2>今日 + 后续</h2>
              </div>
              <button
                type="button"
                className="ghost-button"
                onClick={handleWorkspaceRefresh}
                disabled={isRefreshingWorkspace}
              >
                {isRefreshingWorkspace ? "刷新中..." : "刷新"}
              </button>
            </div>
            {workspaceError ? <p className="status-message error">{workspaceError}</p> : null}
            <div className="workspace-sections">
              <section className="workspace-cluster">
                <div className="workspace-cluster-header">
                  <div>
                    <p className="panel-kicker">今日</p>
                    <h3>当前工作台</h3>
                  </div>
                  <span className="meta-chip">{reviewsToday.length + tasksToday.length} 项</span>
                </div>
                <p className="section-hint">由 `GET /api/v1/workspace/today` 聚合返回，并保持复习 / 任务分区。</p>
                <div className="today-grid">
                  <section className="subpanel review-panel">
                    <div className="subpanel-heading">
                      <h3>复习</h3>
                      <span className="meta-chip">{reviewsToday.length}</span>
                    </div>
                    <p className="subpanel-description">聚焦当前理解不稳或需要回忆强化的 Note。</p>
                    {reviewsToday.length === 0 ? <p className="status-message">今天没有待复习项。</p> : null}
                    {reviewsToday.map((review) => (
                      <div key={review.id} className="list-row review-list-row">
                        <div className="list-row-content">
                          <strong>{review.title}</strong>
                          <p>{review.current_summary}</p>
                          <div className="row-meta row-meta-inline">
                            <span className={getReviewMetaTone(review.queue_type)}>{getReviewQueueLabel(review.queue_type)}</span>
                            <span className={getReviewMetaTone(review.completion_status)}>{getReviewCompletionStatusLabel(review.completion_status)}</span>
                            <span className="tone-slate">未完成 {review.unfinished_count}</span>
                            <span className="tone-slate">{getReviewTimingLabel(review)}</span>
                          </div>
                          <div className="form-actions">
                            <button
                              type="button"
                              className="ghost-button"
                              onClick={() => handleReviewFormToggle(review)}
                              disabled={isSubmittingReview && activeReviewFormId === review.id}
                            >
                              {activeReviewFormId === review.id ? "收起表单" : "完成 / 提交结果"}
                            </button>
                          </div>
                          {activeReviewFormId === review.id && reviewFormDraft ? (
                            <form className="review-form" onSubmit={(event) => handleReviewSubmit(event, review)}>
                              <label className="field-stack">
                                <span className="detail-label">completion_status</span>
                                <select
                                  value={reviewFormDraft.completionStatus}
                                  onChange={(event) => handleReviewDraftChange("completionStatus", event.target.value)}
                                >
                                  {REVIEW_COMPLETION_STATUS_OPTIONS.map((status) => (
                                    <option key={status} value={status}>
                                      {getReviewCompletionStatusLabel(status)}
                                    </option>
                                  ))}
                                </select>
                              </label>
                              {requiresCompletionReason(reviewFormDraft.completionStatus) ? (
                                <label className="field-stack">
                                  <span className="detail-label">completion_reason</span>
                                  <select
                                    value={reviewFormDraft.completionReason}
                                    onChange={(event) => handleReviewDraftChange("completionReason", event.target.value)}
                                  >
                                    <option value="">选择 completion_reason</option>
                                    {REVIEW_COMPLETION_REASON_OPTIONS.map((reason) => (
                                      <option key={reason} value={reason}>
                                        {getReviewCompletionReasonLabel(reason)}
                                      </option>
                                    ))}
                                  </select>
                                </label>
                              ) : null}
                              {isRecallQueue(review.queue_type) ? (
                                <>
                                  <label className="field-stack">
                                    <span className="detail-label">self_recall_result</span>
                                    <select
                                      value={reviewFormDraft.selfRecallResult}
                                      onChange={(event) => handleReviewDraftChange("selfRecallResult", event.target.value)}
                                    >
                                      {REVIEW_SELF_RECALL_RESULT_OPTIONS.map((result) => (
                                        <option key={result} value={result}>
                                          {getSelfRecallResultLabel(result)}
                                        </option>
                                      ))}
                                    </select>
                                  </label>
                                  <label className="field-stack">
                                    <span className="detail-label">note</span>
                                    <textarea
                                      className="compact-textarea"
                                      placeholder="可选补充：本次回忆的简短备注"
                                      value={reviewFormDraft.note}
                                      onChange={(event) => handleReviewDraftChange("note", event.target.value)}
                                    />
                                  </label>
                                </>
                              ) : null}
                              {reviewFormError ? <p className="status-message error">{reviewFormError}</p> : null}
                              <div className="form-actions">
                                <button
                                  type="submit"
                                  disabled={
                                    (isSubmittingReview && activeReviewFormId === review.id) ||
                                    !isReviewFormValid(review, reviewFormDraft)
                                  }
                                >
                                  {isSubmittingReview && activeReviewFormId === review.id ? "提交中..." : "提交结果"}
                                </button>
                                <button type="button" className="ghost-button" onClick={closeReviewForm}>
                                  取消
                                </button>
                              </div>
                            </form>
                          ) : null}
                        </div>
                      </div>
                    ))}
                  </section>
                  <section className="subpanel task-panel">
                    <div className="subpanel-heading">
                      <h3>任务</h3>
                      <span className="meta-chip">{tasksToday.length}</span>
                    </div>
                    <p className="subpanel-description">展示用户任务与系统派生的跟进动作。</p>
                    {tasksToday.length === 0 ? <p className="status-message">今天没有任务。</p> : null}
                    {tasksToday.map((task) => (
                      <div key={task.id} className="list-row">
                        <div className="list-row-content">
                          <strong>{task.title}</strong>
                          <p>{task.description || task.task_type}</p>
                          <div className="row-meta row-meta-inline">
                            <span className={getTaskMetaTone(task.task_source)}>{getTaskSourceLabel(task.task_source)}</span>
                            <span className={getTaskMetaTone(task.status)}>{getTaskStatusLabel(task.status)}</span>
                            <span className="tone-slate">P{task.priority}</span>
                            <span className="tone-slate">{getTaskTimingLabel(task)}</span>
                          </div>
                        </div>
                      </div>
                    ))}
                  </section>
                </div>
              </section>

              <section className="workspace-cluster">
                <div className="workspace-cluster-header">
                  <div>
                    <p className="panel-kicker">后续</p>
                    <h3>基础列表工作台</h3>
                  </div>
                  <span className="meta-chip">{upcomingItemCount} 项</span>
                </div>
                <p className="section-hint">由 `GET /api/v1/workspace/upcoming` 聚合返回，按后端排序展示未来到期项。</p>
                <div className="today-grid">
                  <section className="subpanel review-panel">
                    <div className="subpanel-heading">
                    <h3>后续复习</h3>
                      <div className="meta-chip-stack">
                        <span className="meta-chip">{upcomingReviewGroups.length} 个笔记</span>
                        <span className="meta-chip">{upcomingReviews.length} 个队列</span>
                      </div>
                    </div>
                  <p className="subpanel-description">同一 Note 的回忆补强 / 计划复习会按 `note_id` 分组展示，避免误判成重复条目。</p>
                    {upcomingReviews.length === 0 ? <p className="status-message">当前没有后续复习项。</p> : null}
                    {upcomingReviewGroups.map((group) => (
                      <section key={group.noteId} className="review-group">
                        <div className="review-group-header">
                          <div className="review-group-summary">
                            <strong>{group.title}</strong>
                            <p className="review-group-id">note_id: {group.noteId}</p>
                            <p>{group.currentSummary}</p>
                          </div>
                          <button type="button" className="ghost-button compact-button" onClick={() => setSelectedNoteId(group.noteId)}>
                            打开笔记
                          </button>
                        </div>
                        <div className="review-group-list">
                          {group.reviews.map((review) => (
                            <div key={review.id} className="list-row review-group-item">
                              <div className="list-row-content">
                                <strong>{getReviewQueueLabel(review.queue_type)}</strong>
                                <p>{getUpcomingReviewQueueDescription(review)}</p>
                                <div className="row-meta row-meta-inline">
                                  <span className={getReviewMetaTone(review.queue_type)}>{getReviewQueueLabel(review.queue_type)}</span>
                                  <span className={getReviewMetaTone(review.completion_status)}>{getReviewCompletionStatusLabel(review.completion_status)}</span>
                                  <span className="tone-slate">{review.completion_reason ? getReviewCompletionReasonLabel(review.completion_reason) : "未设置原因"}</span>
                                  <span className="tone-slate">{getReviewTimingLabel(review)}</span>
                                  <span className="tone-slate">未完成 {review.unfinished_count}</span>
                                </div>
                              </div>
                            </div>
                          ))}
                        </div>
                      </section>
                    ))}
                  </section>
                  <section className="subpanel task-panel">
                    <div className="subpanel-heading">
                      <h3>后续任务</h3>
                      <span className="meta-chip">{upcomingTasks.length}</span>
                    </div>
                    <p className="subpanel-description">展示带 `due_at` 的后续动作，保留 `task_source` 区分。</p>
                    {upcomingTasks.length === 0 ? <p className="status-message">当前没有后续任务。</p> : null}
                    {upcomingTasks.map((task) => (
                      <div key={task.id} className="list-row">
                        <div className="list-row-content">
                          <strong>{task.title}</strong>
                          <p>{task.description || task.task_type}</p>
                          <div className="row-meta row-meta-inline">
                            <span className={getTaskMetaTone(task.task_source)}>{getTaskSourceLabel(task.task_source)}</span>
                            <span className={getTaskMetaTone(task.status)}>{getTaskStatusLabel(task.status)}</span>
                            <span className="tone-slate">{task.related_entity_type}</span>
                            <span className="tone-slate">{getTaskTimingLabel(task)}</span>
                          </div>
                        </div>
                      </div>
                    ))}
                  </section>
                </div>
              </section>
            </div>
          </article>
        </div>

        {isDetailVisible ? (
          <article className="panel note-detail-panel">
            <div className="detail-header-shell">
              <div className="panel-heading detail-panel-heading">
                <div>
                  <p className="panel-kicker">笔记详情</p>
                  <h2>{selectedNote?.title ?? "正在加载笔记详情"}</h2>
                </div>
                <div className="detail-toolbar">
                  {selectedNote ? <span className="meta-chip">{selectedNote.latest_content_type ?? "暂无"}</span> : null}
                  <button
                    type="button"
                    className="ghost-button"
                    onClick={() => {
                      setSelectedNoteId(null);
                      setSelectedNote(null);
                      setProposals([]);
                      setNoteError(null);
                      setProposalError(null);
                    }}
                  >
                    关闭详情
                  </button>
                </div>
              </div>
              {isRefreshingNote ? <p className="status-message">正在加载笔记详情...</p> : null}
              {noteError ? <p className="status-message error">{noteError}</p> : null}
            </div>
            {selectedNote ? (
              <>
                <section className="detail-section">
                  <h3>当前解释层</h3>
                  <p className="summary-block">{selectedNote.current_summary}</p>
                  <ul className="key-point-list">
                    {selectedNote.current_key_points.map((point) => (
                      <li key={point}>{point}</li>
                    ))}
                  </ul>
                </section>
                <section className="detail-section detail-meta-grid">
                  <div>
                    <span className="detail-label">source_uri</span>
                    <p>{selectedNote.source_uri ?? "暂无"}</p>
                  </div>
                  <div>
                    <span className="detail-label">created_at</span>
                    <p>{formatDateTime(selectedNote.created_at)}</p>
                  </div>
                  <div>
                    <span className="detail-label">updated_at</span>
                    <p>{formatDateTime(selectedNote.updated_at)}</p>
                  </div>
                </section>
                <section className="detail-section">
                  <h3>原始内容 / 清洗内容</h3>
                  <div className="content-grid">
                    <pre>{selectedNote.raw_text ?? "暂无"}</pre>
                    <pre>{selectedNote.clean_text ?? "暂无"}</pre>
                  </div>
                </section>
                <section className="detail-section">
                  <div className="panel-heading compact">
                    <div>
                      <p className="panel-kicker">证据</p>
                      <h3>证据记录</h3>
                    </div>
                    <span className="meta-chip">{selectedNote.evidence_blocks.length} 条</span>
                  </div>
                  <p className="section-hint">这里展示已保存的 `EVIDENCE` 块，便于确认“保存为证据”已经真正落库。</p>
                  {selectedNote.evidence_blocks.length === 0 ? (
                    <p className="status-message">当前还没有证据记录。</p>
                  ) : (
                    <div className="proposal-list">
                      {selectedNote.evidence_blocks.map((evidence) => (
                        <div key={evidence.id} className="list-row">
                          <div className="list-row-content">
                            <strong>{evidence.source_name ?? "外部补充来源"}</strong>
                            <p>{evidence.summary_snippet ?? "暂无摘要"}</p>
                            {evidence.source_uri ? (
                              <p className="search-result-detail">{evidence.source_uri}</p>
                            ) : null}
                            <div className="row-meta row-meta-inline">
                              <span className="tone-cyan">{evidence.content_type}</span>
                              <span className="tone-blue">{evidence.relation_label ?? "未标注关联类型"}</span>
                              <span className="tone-slate">{formatDateTime(evidence.created_at)}</span>
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </section>
                <section className="detail-section">
                  <div className="panel-heading compact">
                    <div>
                      <p className="panel-kicker">建议</p>
                      <h3>基础展示位</h3>
                    </div>
                    <button
                      type="button"
                      className="ghost-button"
                      onClick={handleProposalCreate}
                      disabled={isMutatingProposal}
                    >
                      {isMutatingProposal ? "处理中..." : "生成建议"}
                    </button>
                  </div>
                  {proposalError ? <p className="status-message error">{proposalError}</p> : null}
                  {proposals.length === 0 ? (
                    <p className="status-message">当前笔记暂无更新建议。</p>
                  ) : (
                    <div className="proposal-list">
                      {proposals.map((proposal) => (
                        <article key={proposal.id} className="proposal-card">
                          <div className="proposal-header">
                            <div>
                              <strong>{proposal.proposal_type}</strong>
                              <p>{proposal.diff_summary}</p>
                            </div>
                            <span className={`status-pill ${getProposalStatusClass(proposal.status)}`}>{getProposalStatusLabel(proposal.status)}</span>
                          </div>
                          <div className="proposal-meta">
                            <span>{getProposalTargetLayerLabel(proposal.target_layer)}</span>
                            <span>{getProposalRiskLabel(proposal.risk_level)}</span>
                            <span>{formatDateTime(proposal.updated_at)}</span>
                          </div>
                          <div className="proposal-summary-grid">
                            <div className="proposal-summary-card">
                              <span className="detail-label">变更前摘要</span>
                              <p>{String(proposal.before_snapshot.current_summary ?? "暂无")}</p>
                            </div>
                            <div className="proposal-summary-card">
                              <span className="detail-label">变更后摘要</span>
                              <p>{String(proposal.after_snapshot.current_summary ?? "暂无")}</p>
                            </div>
                          </div>
                          <div className="form-actions">
                            {canApplyProposal(proposal.status) ? (
                              <button
                                type="button"
                                onClick={() => handleProposalApply(proposal.id)}
                                disabled={isMutatingProposal}
                              >
                                应用
                              </button>
                            ) : null}
                            {proposal.status === "APPLIED" ? (
                              <button
                                type="button"
                                className="ghost-button"
                                onClick={() => handleProposalRollback(proposal.id)}
                                disabled={isMutatingProposal}
                              >
                                回滚
                              </button>
                            ) : null}
                            <span className="meta-chip">trace_id: {proposal.trace_id ?? "暂无"}</span>
                          </div>
                          <details className="proposal-details">
                            <summary>查看变更前后快照</summary>
                            <div className="proposal-snapshots">
                              <div>
                                <span className="detail-label">变更前</span>
                                <pre>{JSON.stringify(proposal.before_snapshot, null, 2)}</pre>
                              </div>
                              <div>
                                <span className="detail-label">变更后</span>
                                <pre>{JSON.stringify(proposal.after_snapshot, null, 2)}</pre>
                              </div>
                            </div>
                          </details>
                        </article>
                      ))}
                    </div>
                  )}
                </section>
              </>
            ) : null}
          </article>
        ) : null}
      </section>
    </main>
  );
}
