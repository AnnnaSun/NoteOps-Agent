import { FormEvent, useEffect, useState, useTransition } from "react";
import {
  applyChangeProposal,
  createCapture,
  createChangeProposal,
  getNote,
  listChangeProposals,
  listNotes,
  listReviewsToday,
  listTasksToday,
  rollbackChangeProposal
} from "./api";
import type { CaptureResponse, ChangeProposal, NoteDetail, NoteSummary, ReviewTodayItem, TaskItem } from "./types";

const DEFAULT_USER_ID = "11111111-1111-1111-1111-111111111111";
const USER_ID_STORAGE_KEY = "noteops-user-id";

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

export default function App() {
  const [userIdInput, setUserIdInput] = useState(readStoredUserId);
  const [activeUserId, setActiveUserId] = useState(readStoredUserId);
  const [notes, setNotes] = useState<NoteSummary[]>([]);
  const [selectedNoteId, setSelectedNoteId] = useState<string | null>(null);
  const [selectedNote, setSelectedNote] = useState<NoteDetail | null>(null);
  const [proposals, setProposals] = useState<ChangeProposal[]>([]);
  const [reviewsToday, setReviewsToday] = useState<ReviewTodayItem[]>([]);
  const [tasksToday, setTasksToday] = useState<TaskItem[]>([]);
  const [captureResult, setCaptureResult] = useState<CaptureResponse | null>(null);
  const [captureInputType, setCaptureInputType] = useState<"TEXT" | "URL">("TEXT");
  const [captureText, setCaptureText] = useState("");
  const [captureSourceUri, setCaptureSourceUri] = useState("");
  const [notesError, setNotesError] = useState<string | null>(null);
  const [todayError, setTodayError] = useState<string | null>(null);
  const [noteError, setNoteError] = useState<string | null>(null);
  const [proposalError, setProposalError] = useState<string | null>(null);
  const [captureError, setCaptureError] = useState<string | null>(null);
  const [isBootstrapping, setIsBootstrapping] = useState(true);
  const [isSubmittingCapture, startCaptureTransition] = useTransition();
  const [isRefreshingToday, startTodayTransition] = useTransition();
  const [isRefreshingNote, startNoteTransition] = useTransition();
  const [isMutatingProposal, startProposalTransition] = useTransition();
  const isDetailVisible = Boolean(selectedNoteId || noteError || isRefreshingNote);
  const selectedNoteSummary = selectedNote?.current_summary ?? "当前没有展开的 Note。";

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
      setTodayError(null);
      try {
        const [noteItems, reviewItems, taskItems] = await Promise.all([
          listNotes(activeUserId),
          listReviewsToday(activeUserId),
          listTasksToday(activeUserId, getTimezoneOffsetLabel())
        ]);
        if (cancelled) {
          return;
        }
        setNotes(noteItems);
        setReviewsToday(reviewItems);
        setTasksToday(taskItems);
        setSelectedNoteId(noteItems[0]?.id ?? null);
      } catch (error) {
        if (cancelled) {
          return;
        }
        const message = error instanceof Error ? error.message : "Failed to load data";
        setNotesError(message);
        setTodayError(message);
        setNotes([]);
        setReviewsToday([]);
        setTasksToday([]);
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
          startTodayTransition(() => {
            void (async () => {
              try {
                const [reviewItems, taskItems] = await Promise.all([
                  listReviewsToday(activeUserId),
                  listTasksToday(activeUserId, getTimezoneOffsetLabel())
                ]);
                setReviewsToday(reviewItems);
                setTasksToday(taskItems);
                setTodayError(null);
              } catch (error) {
                setTodayError(error instanceof Error ? error.message : "Failed to refresh today");
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

  function handleTodayRefresh() {
    startTodayTransition(() => {
      void (async () => {
        setTodayError(null);
        try {
          const [reviewItems, taskItems] = await Promise.all([
            listReviewsToday(activeUserId),
            listTasksToday(activeUserId, getTimezoneOffsetLabel())
          ]);
          setReviewsToday(reviewItems);
          setTasksToday(taskItems);
        } catch (error) {
          setTodayError(error instanceof Error ? error.message : "Failed to refresh today");
        }
      })();
    });
  }

  return (
    <main className="app-shell">
      <section className="hero-panel">
        <div>
          <p className="eyebrow">Phase 1 / M7</p>
          <h1>Note-first knowledge kernel, now with a real web surface.</h1>
          <p className="hero-copy">
            单页骨架直接联到现有 Capture、Note、Review、Task 和 Proposal API，保持 Phase 1 的最小可验证闭环。
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
          <span className="overview-label">Focus</span>
          <strong>{isDetailVisible ? "Detail Open" : "Browse Mode"}</strong>
          <p>{selectedNoteSummary}</p>
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
                <p className="panel-kicker">Today</p>
                <h2>Review + Task</h2>
              </div>
              <button type="button" className="ghost-button" onClick={handleTodayRefresh} disabled={isRefreshingToday}>
                {isRefreshingToday ? "刷新中..." : "刷新"}
              </button>
            </div>
            {todayError ? <p className="status-message error">{todayError}</p> : null}
            <div className="today-grid">
              <section className="subpanel review-panel">
                <div className="subpanel-heading">
                  <h3>Reviews</h3>
                  <span className="meta-chip">{reviewsToday.length}</span>
                </div>
                <p className="subpanel-description">聚焦当前理解不稳或需要回忆强化的 Note。</p>
                {reviewsToday.length === 0 ? <p className="status-message">今天没有待复习项。</p> : null}
                {reviewsToday.map((review) => (
                  <div key={review.id} className="list-row">
                    <div className="list-row-content">
                      <strong>{review.title}</strong>
                      <p>{review.current_summary}</p>
                      <div className="row-meta row-meta-inline">
                        <span className={getReviewMetaTone(review.queue_type)}>{review.queue_type}</span>
                        <span className={getReviewMetaTone(review.completion_status)}>{review.completion_status}</span>
                        <span className="tone-slate">{review.unfinished_count} unfinished</span>
                      </div>
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
                      </div>
                    </div>
                  </div>
                ))}
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
