import type { ReviewCompletionPayload, SyncActionRequestItem } from "./types";

const SYNC_CLIENT_ID_STORAGE_KEY = "noteops-sync-client-id";
const PENDING_SYNC_ACTIONS_STORAGE_KEY = "noteops-pending-sync-actions";

type PendingSyncAction = SyncActionRequestItem & {
  user_id: string;
};

function canUseLocalStorage(): boolean {
  return typeof window !== "undefined" && typeof window.localStorage !== "undefined";
}

function readPendingActions(): PendingSyncAction[] {
  if (!canUseLocalStorage()) {
    return [];
  }
  const raw = window.localStorage.getItem(PENDING_SYNC_ACTIONS_STORAGE_KEY);
  if (!raw) {
    return [];
  }
  try {
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed.filter((item): item is PendingSyncAction => {
      if (!item || typeof item !== "object") {
        return false;
      }
      return (
        typeof item.user_id === "string" &&
        typeof item.offline_action_id === "string" &&
        typeof item.action_type === "string" &&
        typeof item.entity_type === "string" &&
        typeof item.entity_id === "string" &&
        typeof item.occurred_at === "string" &&
        item.payload != null &&
        typeof item.payload === "object"
      );
    });
  } catch {
    return [];
  }
}

function writePendingActions(actions: PendingSyncAction[]): void {
  if (!canUseLocalStorage()) {
    return;
  }
  window.localStorage.setItem(PENDING_SYNC_ACTIONS_STORAGE_KEY, JSON.stringify(actions));
}

function createOfflineActionId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `offline-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`;
}

export function getSyncClientId(): string {
  if (!canUseLocalStorage()) {
    return "web-client-runtime";
  }
  const existing = window.localStorage.getItem(SYNC_CLIENT_ID_STORAGE_KEY);
  if (existing && existing.trim()) {
    return existing;
  }
  const created = createOfflineActionId();
  window.localStorage.setItem(SYNC_CLIENT_ID_STORAGE_KEY, created);
  return created;
}

export function enqueuePendingReviewCompleteAction(
  userId: string,
  reviewId: string,
  payload: ReviewCompletionPayload
): PendingSyncAction {
  const pending = readPendingActions();
  const action: PendingSyncAction = {
    user_id: userId,
    offline_action_id: createOfflineActionId(),
    action_type: "REVIEW_COMPLETE",
    entity_type: "REVIEW_STATE",
    entity_id: reviewId,
    payload: { ...payload },
    occurred_at: new Date().toISOString()
  };
  pending.push(action);
  writePendingActions(pending);
  return action;
}

export function listPendingActionsByUser(userId: string): PendingSyncAction[] {
  return readPendingActions().filter((item) => item.user_id === userId);
}

export function removePendingActions(userId: string, offlineActionIds: string[]): void {
  if (!offlineActionIds.length) {
    return;
  }
  const removeSet = new Set(offlineActionIds);
  const next = readPendingActions().filter(
    (item) => !(item.user_id === userId && removeSet.has(item.offline_action_id))
  );
  writePendingActions(next);
}

export function countPendingActionsByUser(userId: string): number {
  return listPendingActionsByUser(userId).length;
}
