import { FormEvent, useEffect, useState, useTransition } from "react";
import {
  applyChangeProposal,
  completeReview,
  createCapture,
  createChangeProposal,
  getWorkspaceToday,
  getWorkspaceUpcoming,
  getNote,
  listChangeProposals,
  listNotes,
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
    return "N/A";
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
  return task.due_at ? `截止 ${formatDateTime(task.due_at)}` : "无 due_at";
}

function getUpcomingReviewQueueDescription(review: ReviewTodayItem): string {
  return review.queue_type === "RECALL"
    ? "短期 recall 回补队列，用来快速追踪未掌握内容。"
    : "长期 schedule 节奏队列，用来保留正常复习周期。";
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
  const [reviewFormError, setReviewFormError] = useState<string | null>(null);
  const [isBootstrapping, setIsBootstrapping] = useState(true);
  const [isSubmittingCapture, startCaptureTransition] = useTransition();
  const [isSearching, startSearchTransition] = useTransition();
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
        const message = error instanceof Error ? error.message : "Failed to load data";
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
          const message = error instanceof Error ? error.message : "Failed to load note detail";
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
    startCaptureTransition(() => {
      void (async () => {
        setCaptureError(null);
        try {
          const result = await createCapture({
            userId: activeUserId,
            inputType: captureInputType,
            rawInput: captureText,
            sourceUri: captureInputType === "URL" ? captureSourceUri : undefined
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
                setWorkspaceError(error instanceof Error ? error.message : "Failed to refresh workspace");
              }
            })();
          });
        } catch (error) {
          setCaptureError(error instanceof Error ? error.message : "Failed to submit capture");
        }
      })();
    });
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
          setProposalError(error instanceof Error ? error.message : "Failed to create proposal");
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
          setProposalError(error instanceof Error ? error.message : "Failed to apply proposal");
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
          setProposalError(error instanceof Error ? error.message : "Failed to rollback proposal");
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
          setWorkspaceError(error instanceof Error ? error.message : "Failed to refresh workspace");
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
    startSearchTransition(() => {
      void (async () => {
        setSearchError(null);
        try {
          const result = await searchNotes(activeUserId, query);
          setSearchResult(result);
        } catch (error) {
          setSearchResult(null);
          setSearchError(error instanceof Error ? error.message : "Failed to search notes");
        }
      })();
    });
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
          setReviewFormError(error instanceof Error ? error.message : "Failed to complete review");
        }
      })();
    });
  }

  return (
    <main className="app-shell">
      <section className="hero-panel">
        <div>
          <p className="eyebrow">Phase 2 / Step 2.7</p>
          <h1>Search、Review 与 Workspace 主路径已连真实接口。</h1>
          <p className="hero-copy">
            当前单页继续保留 Capture、Note 和 Proposal 主链路，并补齐 Search 三分栏与 Today Review
            完成表单，保持 Step 2.7 的最小可验收闭环。
          </p>
        </div>
        <form className="user-form" onSubmit={handleUserIdApply}>
          <label htmlFor="user-id">Active user_id</label>
          <input
            id="user-id"
            value={userIdInput}
            onChange={(event) => setUserIdInput(event.target.value)}
            spellCheck={false}
          />
          <button type="submit">切换上下文</button>
        </form>
      </section>

      <section className="workspace-overview" aria-label="Workspace overview">
        <article className="overview-card">
          <span className="overview-label">Notes</span>
          <strong>{notes.length}</strong>
          <p>当前解释层可浏览条目</p>
        </article>
        <article className="overview-card">
          <span className="overview-label">Today Reviews</span>
          <strong>{reviewsToday.length}</strong>
          <p>今日复习项</p>
        </article>
        <article className="overview-card">
          <span className="overview-label">Today Tasks</span>
          <strong>{tasksToday.length}</strong>
          <p>待办与跟进任务</p>
        </article>
        <article className="overview-card overview-card-wide">
          <span className="overview-label">Upcoming Queue</span>
          <strong>{upcomingItemCount}</strong>
          <p>未来待处理的 Review 与 Task</p>
        </article>
      </section>

      <section className={`workspace-grid ${isDetailVisible ? "detail-open" : "detail-closed"}`}>
        <div className="column-stack">
          <article className="panel">
            <div className="panel-heading">
              <div>
                <p className="panel-kicker">Capture</p>
                <h2>提交一条新输入</h2>
              </div>
            </div>
            <form className="capture-form" onSubmit={handleCaptureSubmit}>
              <div className="segmented-control" role="tablist" aria-label="Capture input type">
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
              <textarea
                placeholder={captureInputType === "TEXT" ? "输入要落库的正文内容..." : "输入 URL 说明或抓取原文占位..."}
                value={captureText}
                onChange={(event) => setCaptureText(event.target.value)}
              />
              <div className="form-actions">
                <button type="submit" disabled={isSubmittingCapture || !captureText.trim()}>
                  {isSubmittingCapture ? "提交中..." : "提交 Capture"}
                </button>
                <span className="meta-chip">user_id: {activeUserId}</span>
              </div>
            </form>
            {captureError ? <p className="status-message error">{captureError}</p> : null}
            {captureResult ? (
              <div className="status-card">
                <div className="status-card-header">
                  <strong>最近提交：{captureResult.status}</strong>
                  {captureResult.note_id ? (
                    <button
                      type="button"
                      className="ghost-button"
                      onClick={() => setSelectedNoteId(captureResult.note_id)}
                    >
                      查看新 Note
                    </button>
                  ) : null}
                </div>
                <p>capture_id: {captureResult.id}</p>
                <p>note_id: {captureResult.note_id ?? "N/A"}</p>
              </div>
            ) : null}
          </article>

          <article className="panel">
            <div className="panel-heading">
              <div>
                <p className="panel-kicker">Search</p>
                <h2>三分栏结果</h2>
              </div>
              <span className="meta-chip">API: /api/v1/search</span>
            </div>
            <form className="search-form" onSubmit={handleSearchSubmit}>
              <input
                placeholder="输入 query，例如 kickoff alpha"
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value)}
                spellCheck={false}
              />
              <div className="form-actions">
                <button type="submit" disabled={isSearching || !searchQuery.trim()}>
                  {isSearching ? "查询中..." : "执行 Search"}
                </button>
                {searchResult ? <span className="meta-chip">query: {searchResult.query}</span> : null}
              </div>
            </form>
            {searchError ? <p className="status-message error">{searchError}</p> : null}
            {!hasSearched ? (
              <p className="status-message">输入 query 后执行 Search，结果会按 exact / related / external 三分栏展示。</p>
            ) : (
              <div className="search-grid">
                <section className="subpanel search-panel-exact">
                  <div className="subpanel-heading">
                    <h3>Exact Matches</h3>
                    <span className="meta-chip">{exactMatches.length}</span>
                  </div>
                  <p className="subpanel-description">命中当前 Note 解释层或最新内容的直接匹配。</p>
                  {exactMatches.length === 0 ? <p className="status-message">本次查询没有 exact match。</p> : null}
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
                            打开 Note
                          </button>
                        </div>
                      </div>
                    </div>
                  ))}
                </section>

                <section className="subpanel search-panel-related">
                  <div className="subpanel-heading">
                    <h3>Related Matches</h3>
                    <span className="meta-chip">{relatedMatches.length}</span>
                  </div>
                  <p className="subpanel-description">展示与 query 存在 token 关联的内部 Note。</p>
                  {relatedMatches.length === 0 ? <p className="status-message">本次查询没有 related match。</p> : null}
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
                          <span className="tone-slate">{formatDateTime(match.updated_at)}</span>
                        </div>
                        <div className="form-actions">
                          <button type="button" className="ghost-button" onClick={() => setSelectedNoteId(match.note_id)}>
                            打开 Note
                          </button>
                        </div>
                      </div>
                    </div>
                  ))}
                </section>

                <section className="subpanel search-panel-external">
                  <div className="subpanel-heading">
                    <h3>External Supplements</h3>
                    <span className="meta-chip">{externalSupplements.length}</span>
                  </div>
                  <p className="subpanel-description">只读展示外部补充信息，不在本步执行 evidence/proposal 动作。</p>
                  {externalSupplements.length === 0 ? <p className="status-message">本次查询没有 external supplement。</p> : null}
                  {externalSupplements.map((item) => (
                    <div key={`${item.source_uri}-${item.summary}`} className="list-row">
                      <div className="list-row-content">
                        <strong>{item.source_name}</strong>
                        <p>{item.summary}</p>
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
                          {item.relation_tags.map((tag) => (
                            <span key={`${item.source_uri}-${tag}`} className="tone-cyan">
                              {tag}
                            </span>
                          ))}
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
                <p className="panel-kicker">Notes</p>
                <h2>当前解释层列表</h2>
              </div>
              <span className="meta-chip">{notes.length} items</span>
            </div>
            <p className="section-hint">点击 Note 查看详情，再次点击当前项或右侧关闭按钮可收起详情面板。</p>
            {isBootstrapping ? <p className="status-message">正在加载 Note 列表...</p> : null}
            {notesError ? <p className="status-message error">{notesError}</p> : null}
            {!isBootstrapping && !notesError && notes.length === 0 ? (
              <p className="status-message">当前 `user_id` 还没有 Note。先提交一条 TEXT capture。</p>
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
                    <span className="note-card-pill">{selectedNoteId === note.id ? "OPEN" : "VIEW"}</span>
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
                  <span className="note-card-meta">updated {formatDateTime(note.updated_at)}</span>
                </button>
              ))}
            </div>
          </article>

          <article className="panel">
            <div className="panel-heading">
              <div>
                <p className="panel-kicker">Workspace</p>
                <h2>Today + Upcoming</h2>
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
                    <p className="panel-kicker">Today</p>
                    <h3>当前工作台</h3>
                  </div>
                  <span className="meta-chip">{reviewsToday.length + tasksToday.length} items</span>
                </div>
                <p className="section-hint">由 `GET /api/v1/workspace/today` 聚合返回，并保持 Review / Task 分区。</p>
                <div className="today-grid">
                  <section className="subpanel review-panel">
                    <div className="subpanel-heading">
                      <h3>Reviews</h3>
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
                            <span className={getReviewMetaTone(review.queue_type)}>{review.queue_type}</span>
                            <span className={getReviewMetaTone(review.completion_status)}>{review.completion_status}</span>
                            <span className="tone-slate">{review.unfinished_count} unfinished</span>
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
                                      {status}
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
                                        {reason}
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
                                          {result}
                                        </option>
                                      ))}
                                    </select>
                                  </label>
                                  <label className="field-stack">
                                    <span className="detail-label">note</span>
                                    <textarea
                                      className="compact-textarea"
                                      placeholder="可选补充：本次 recall 的简短备注"
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
                      <h3>Tasks</h3>
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
                            <span className={getTaskMetaTone(task.task_source)}>{task.task_source}</span>
                            <span className={getTaskMetaTone(task.status)}>{task.status}</span>
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
                    <p className="panel-kicker">Upcoming</p>
                    <h3>基础列表工作台</h3>
                  </div>
                  <span className="meta-chip">{upcomingItemCount} items</span>
                </div>
                <p className="section-hint">由 `GET /api/v1/workspace/upcoming` 聚合返回，按后端排序展示未来到期项。</p>
                <div className="today-grid">
                  <section className="subpanel review-panel">
                    <div className="subpanel-heading">
                      <h3>Upcoming Reviews</h3>
                      <div className="meta-chip-stack">
                        <span className="meta-chip">{upcomingReviewGroups.length} notes</span>
                        <span className="meta-chip">{upcomingReviews.length} queues</span>
                      </div>
                    </div>
                    <p className="subpanel-description">同一 Note 的 recall / schedule 会按 `note_id` 分组展示，避免误判成重复条目。</p>
                    {upcomingReviews.length === 0 ? <p className="status-message">当前没有 upcoming review。</p> : null}
                    {upcomingReviewGroups.map((group) => (
                      <section key={group.noteId} className="review-group">
                        <div className="review-group-header">
                          <div className="review-group-summary">
                            <strong>{group.title}</strong>
                            <p className="review-group-id">note_id: {group.noteId}</p>
                            <p>{group.currentSummary}</p>
                          </div>
                          <button type="button" className="ghost-button compact-button" onClick={() => setSelectedNoteId(group.noteId)}>
                            打开 Note
                          </button>
                        </div>
                        <div className="review-group-list">
                          {group.reviews.map((review) => (
                            <div key={review.id} className="list-row review-group-item">
                              <div className="list-row-content">
                                <strong>{review.queue_type} queue</strong>
                                <p>{getUpcomingReviewQueueDescription(review)}</p>
                                <div className="row-meta row-meta-inline">
                                  <span className={getReviewMetaTone(review.queue_type)}>{review.queue_type}</span>
                                  <span className={getReviewMetaTone(review.completion_status)}>{review.completion_status}</span>
                                  <span className="tone-slate">{review.completion_reason ?? "未设 reason"}</span>
                                  <span className="tone-slate">{getReviewTimingLabel(review)}</span>
                                  <span className="tone-slate">unfinished {review.unfinished_count}</span>
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
                      <h3>Upcoming Tasks</h3>
                      <span className="meta-chip">{upcomingTasks.length}</span>
                    </div>
                    <p className="subpanel-description">展示带 `due_at` 的后续动作，保留 `task_source` 区分。</p>
                    {upcomingTasks.length === 0 ? <p className="status-message">当前没有 upcoming task。</p> : null}
                    {upcomingTasks.map((task) => (
                      <div key={task.id} className="list-row">
                        <div className="list-row-content">
                          <strong>{task.title}</strong>
                          <p>{task.description || task.task_type}</p>
                          <div className="row-meta row-meta-inline">
                            <span className={getTaskMetaTone(task.task_source)}>{task.task_source}</span>
                            <span className={getTaskMetaTone(task.status)}>{task.status}</span>
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
                  <p className="panel-kicker">Note Detail</p>
                  <h2>{selectedNote?.title ?? "正在加载详情"}</h2>
                </div>
                <div className="detail-toolbar">
                  {selectedNote ? <span className="meta-chip">{selectedNote.latest_content_type ?? "N/A"}</span> : null}
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
              {isRefreshingNote ? <p className="status-message">正在加载 Note 详情...</p> : null}
              {noteError ? <p className="status-message error">{noteError}</p> : null}
            </div>
            {selectedNote ? (
              <>
                <section className="detail-section">
                  <h3>Interpretation</h3>
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
                    <p>{selectedNote.source_uri ?? "N/A"}</p>
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
                  <h3>Raw / Clean Content</h3>
                  <div className="content-grid">
                    <pre>{selectedNote.raw_text ?? "N/A"}</pre>
                    <pre>{selectedNote.clean_text ?? "N/A"}</pre>
                  </div>
                </section>
                <section className="detail-section">
                  <div className="panel-heading compact">
                    <div>
                      <p className="panel-kicker">Proposal</p>
                      <h3>基础展示位</h3>
                    </div>
                    <button
                      type="button"
                      className="ghost-button"
                      onClick={handleProposalCreate}
                      disabled={isMutatingProposal}
                    >
                      {isMutatingProposal ? "处理中..." : "生成 Proposal"}
                    </button>
                  </div>
                  {proposalError ? <p className="status-message error">{proposalError}</p> : null}
                  {proposals.length === 0 ? (
                    <p className="status-message">当前 Note 暂无 proposal。</p>
                  ) : (
                    <div className="proposal-list">
                      {proposals.map((proposal) => (
                        <article key={proposal.id} className="proposal-card">
                          <div className="proposal-header">
                            <div>
                              <strong>{proposal.proposal_type}</strong>
                              <p>{proposal.diff_summary}</p>
                            </div>
                            <span className={`status-pill ${getProposalStatusClass(proposal.status)}`}>{proposal.status}</span>
                          </div>
                          <div className="proposal-meta">
                            <span>{proposal.target_layer}</span>
                            <span>{proposal.risk_level}</span>
                            <span>{formatDateTime(proposal.updated_at)}</span>
                          </div>
                          <div className="proposal-summary-grid">
                            <div className="proposal-summary-card">
                              <span className="detail-label">before summary</span>
                              <p>{String(proposal.before_snapshot.current_summary ?? "N/A")}</p>
                            </div>
                            <div className="proposal-summary-card">
                              <span className="detail-label">after summary</span>
                              <p>{String(proposal.after_snapshot.current_summary ?? "N/A")}</p>
                            </div>
                          </div>
                          <div className="form-actions">
                            {canApplyProposal(proposal.status) ? (
                              <button
                                type="button"
                                onClick={() => handleProposalApply(proposal.id)}
                                disabled={isMutatingProposal}
                              >
                                Apply
                              </button>
                            ) : null}
                            {proposal.status === "APPLIED" ? (
                              <button
                                type="button"
                                className="ghost-button"
                                onClick={() => handleProposalRollback(proposal.id)}
                                disabled={isMutatingProposal}
                              >
                                Rollback
                              </button>
                            ) : null}
                            <span className="meta-chip">trace_id: {proposal.trace_id ?? "N/A"}</span>
                          </div>
                          <details className="proposal-details">
                            <summary>查看 before / after snapshots</summary>
                            <div className="proposal-snapshots">
                              <div>
                                <span className="detail-label">before</span>
                                <pre>{JSON.stringify(proposal.before_snapshot, null, 2)}</pre>
                              </div>
                              <div>
                                <span className="detail-label">after</span>
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
