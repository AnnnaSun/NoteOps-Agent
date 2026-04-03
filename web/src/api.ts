import type {
  ApiEnvelope,
  CaptureResponse,
  ChangeProposal,
  NoteDetail,
  NoteSummary,
  ReviewFeedbackResult,
  ReviewCompletionPayload,
  ReviewCompletionResult,
  ReviewPrepResult,
  ReviewTodayItem,
  SearchEvidenceResult,
  SearchResult,
  TaskItem,
  WorkspaceToday,
  WorkspaceUpcoming
} from "./types";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {})
    },
    ...init
  });

  let envelope: ApiEnvelope<T>;
  try {
    envelope = (await response.json()) as ApiEnvelope<T>;
  } catch {
    throw new Error(`Unexpected response from ${path}`);
  }

  if (!response.ok || !envelope.success || envelope.data == null) {
    throw new Error(envelope.error?.message ?? `Request failed for ${path}`);
  }
  return envelope.data;
}

export function listNotes(userId: string): Promise<NoteSummary[]> {
  return request(`/api/v1/notes?user_id=${encodeURIComponent(userId)}`);
}

export function getNote(noteId: string, userId: string): Promise<NoteDetail> {
  return request(`/api/v1/notes/${encodeURIComponent(noteId)}?user_id=${encodeURIComponent(userId)}`);
}

export function createCapture(input: {
  userId: string;
  sourceType: "TEXT" | "URL";
  rawText?: string;
  sourceUrl?: string;
  titleHint?: string;
}): Promise<CaptureResponse> {
  return request("/api/v1/captures", {
    method: "POST",
    body: JSON.stringify({
      user_id: input.userId,
      source_type: input.sourceType,
      raw_text: input.rawText ?? null,
      source_url: input.sourceUrl ?? null,
      title_hint: input.titleHint ?? null
    })
  });
}

export function listReviewsToday(userId: string): Promise<ReviewTodayItem[]> {
  return request(`/api/v1/reviews/today?user_id=${encodeURIComponent(userId)}`);
}

function buildReviewScopedParams(userId: string): string {
  return new URLSearchParams({
    user_id: userId
  }).toString();
}

export function getReviewPrep(reviewItemId: string, userId: string): Promise<ReviewPrepResult> {
  return request(
    `/api/v1/reviews/${encodeURIComponent(reviewItemId)}/prep?${buildReviewScopedParams(userId)}`
  );
}

export function getReviewFeedback(reviewItemId: string, userId: string): Promise<ReviewFeedbackResult> {
  return request(
    `/api/v1/reviews/${encodeURIComponent(reviewItemId)}/feedback?${buildReviewScopedParams(userId)}`
  );
}

export function listTasksToday(userId: string, timezoneOffset: string): Promise<TaskItem[]> {
  const params = new URLSearchParams({
    user_id: userId,
    timezone_offset: timezoneOffset
  });
  return request(`/api/v1/tasks/today?${params.toString()}`);
}

function buildWorkspaceParams(userId: string, timezoneOffset: string): string {
  const params = new URLSearchParams({
    user_id: userId,
    timezone_offset: timezoneOffset
  });
  return params.toString();
}

export function getWorkspaceToday(userId: string, timezoneOffset: string): Promise<WorkspaceToday> {
  return request(`/api/v1/workspace/today?${buildWorkspaceParams(userId, timezoneOffset)}`);
}

export function getWorkspaceUpcoming(userId: string, timezoneOffset: string): Promise<WorkspaceUpcoming> {
  return request(`/api/v1/workspace/upcoming?${buildWorkspaceParams(userId, timezoneOffset)}`);
}

export function searchNotes(userId: string, query: string): Promise<SearchResult> {
  const params = new URLSearchParams({
    user_id: userId,
    query
  });
  return request(`/api/v1/search?${params.toString()}`);
}

type SearchSupplementActionInput = {
  userId: string;
  query: string;
  sourceName: string;
  sourceUri: string;
  summary: string;
  keywords: string[];
  relationLabel: string;
  relationTags: string[];
  summarySnippet: string;
};

function searchSupplementActionBody(input: SearchSupplementActionInput): string {
  return JSON.stringify({
    user_id: input.userId,
    query: input.query,
    source_name: input.sourceName,
    source_uri: input.sourceUri,
    summary: input.summary,
    keywords: input.keywords,
    relation_label: input.relationLabel,
    relation_tags: input.relationTags,
    summary_snippet: input.summarySnippet
  });
}

export function saveSearchEvidence(noteId: string, input: SearchSupplementActionInput): Promise<SearchEvidenceResult> {
  return request(`/api/v1/search/notes/${encodeURIComponent(noteId)}/evidence`, {
    method: "POST",
    body: searchSupplementActionBody(input)
  });
}

export function createSearchChangeProposal(noteId: string, input: SearchSupplementActionInput): Promise<ChangeProposal> {
  return request(`/api/v1/search/notes/${encodeURIComponent(noteId)}/change-proposals`, {
    method: "POST",
    body: searchSupplementActionBody(input)
  });
}

export function completeReview(
  reviewItemId: string,
  payload: ReviewCompletionPayload
): Promise<ReviewCompletionResult> {
  return request(`/api/v1/reviews/${encodeURIComponent(reviewItemId)}/complete`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function listChangeProposals(noteId: string, userId: string): Promise<ChangeProposal[]> {
  return request(
    `/api/v1/notes/${encodeURIComponent(noteId)}/change-proposals?user_id=${encodeURIComponent(userId)}`
  );
}

export function createChangeProposal(noteId: string, userId: string): Promise<ChangeProposal> {
  return request(`/api/v1/notes/${encodeURIComponent(noteId)}/change-proposals`, {
    method: "POST",
    body: JSON.stringify({
      user_id: userId
    })
  });
}

export function applyChangeProposal(noteId: string, proposalId: string, userId: string): Promise<ChangeProposal> {
  return request(
    `/api/v1/notes/${encodeURIComponent(noteId)}/change-proposals/${encodeURIComponent(proposalId)}/apply`,
    {
      method: "POST",
      body: JSON.stringify({
        user_id: userId
      })
    }
  );
}

export function rollbackChangeProposal(proposalId: string, userId: string): Promise<ChangeProposal> {
  return request(`/api/v1/change-proposals/${encodeURIComponent(proposalId)}/rollback`, {
    method: "POST",
    body: JSON.stringify({
      user_id: userId
    })
  });
}
