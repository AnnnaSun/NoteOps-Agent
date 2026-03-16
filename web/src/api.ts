import type {
  ApiEnvelope,
  CaptureResponse,
  ChangeProposal,
  NoteDetail,
  NoteSummary,
  ReviewTodayItem,
  TaskItem
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
  inputType: "TEXT" | "URL";
  rawInput: string;
  sourceUri?: string;
}): Promise<CaptureResponse> {
  return request("/api/v1/captures", {
    method: "POST",
    body: JSON.stringify({
      user_id: input.userId,
      input_type: input.inputType,
      raw_input: input.rawInput,
      source_uri: input.sourceUri ?? null
    })
  });
}

export function listReviewsToday(userId: string): Promise<ReviewTodayItem[]> {
  return request(`/api/v1/reviews/today?user_id=${encodeURIComponent(userId)}`);
}

export function listTasksToday(userId: string, timezoneOffset: string): Promise<TaskItem[]> {
  const params = new URLSearchParams({
    user_id: userId,
    timezone_offset: timezoneOffset
  });
  return request(`/api/v1/tasks/today?${params.toString()}`);
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
