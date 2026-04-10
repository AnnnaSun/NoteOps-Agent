type IdentifiedItem = {
  id: string;
};

export type BootstrapSliceState<T> = {
  data: T;
  error: string | null;
};

export function toErrorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback;
}

export function foldBootstrapSlice<T>(
  result: PromiseSettledResult<T>,
  fallbackData: T
): BootstrapSliceState<T> {
  if (result.status === "fulfilled") {
    return {
      data: result.value,
      error: null
    };
  }
  return {
    data: fallbackData,
    error: toErrorMessage(result.reason, "加载数据失败")
  };
}

export function resolveIdeaSelection<T extends IdentifiedItem>(
  ideaItems: T[],
  preferredIdeaId: string | null | undefined,
  currentSelectedIdeaId: string | null | undefined,
  preserveCurrentSelection: boolean
): string | null {
  if (ideaItems.length === 0) {
    return null;
  }

  if (
    preserveCurrentSelection &&
    currentSelectedIdeaId &&
    ideaItems.some((idea) => idea.id === currentSelectedIdeaId)
  ) {
    return currentSelectedIdeaId;
  }

  if (preferredIdeaId && ideaItems.some((idea) => idea.id === preferredIdeaId)) {
    return preferredIdeaId;
  }

  if (currentSelectedIdeaId && ideaItems.some((idea) => idea.id === currentSelectedIdeaId)) {
    return currentSelectedIdeaId;
  }

  return ideaItems[0]?.id ?? null;
}

export function shouldApplyIdeaActionFollowUp(
  actionIdeaId: string,
  currentSelectedIdeaId: string | null | undefined
): boolean {
  return currentSelectedIdeaId === actionIdeaId;
}
