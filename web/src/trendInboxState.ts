import type { TrendAnalysisPayload, TrendInboxItem } from "./types";

export type TrendInboxFilters = {
  status: string;
  sourceType: string;
};

export type TrendInboxActionKey = "IGNORE" | "SAVE_AS_NOTE" | "PROMOTE_TO_IDEA";

export type TrendInboxActionModel = {
  key: TrendInboxActionKey;
  label: string;
  isEnabled: boolean;
  disabledReason: string | null;
};

export type TrendInboxCardModel = {
  trendItemId: string;
  title: string;
  url: string;
  summary: string;
  sourceType: string;
  sourceLabel: string;
  scoreLabel: string;
  status: string;
  statusLabel: string;
  suggestedAction: string | null;
  suggestedActionLabel: string;
  whyItMatters: string | null;
  topicTags: string[];
  signalType: string | null;
  reasoningSummary: string | null;
  updatedAtLabel: string;
  actions: TrendInboxActionModel[];
};

const DEFAULT_DISABLED_REASON = "该动作将在 Step 4.6 完成。";

export const TREND_INBOX_DEFAULT_FILTERS: TrendInboxFilters = {
  status: "ANALYZED",
  sourceType: "ALL"
};

export const TREND_INBOX_STATUS_OPTIONS: Array<{ value: string; label: string }> = [
  { value: "ANALYZED", label: "Analyzed" },
  { value: "INGESTED", label: "Ingested" },
  { value: "IGNORED", label: "Ignored" }
];

export const TREND_INBOX_SOURCE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: "ALL", label: "All" },
  { value: "HN", label: "HN" },
  { value: "GITHUB", label: "GitHub" }
];

export function normalizeTrendAnalysisPayload(payload: TrendAnalysisPayload | null | undefined): {
  whyItMatters: string | null;
  topicTags: string[];
  signalType: string | null;
  reasoningSummary: string | null;
} {
  const whyItMatters = typeof payload?.why_it_matters === "string" ? payload.why_it_matters : null;
  const topicTags = Array.isArray(payload?.topic_tags) ? payload.topic_tags.map(String) : [];
  const signalType = typeof payload?.signal_type === "string" ? payload.signal_type : null;
  const reasoningSummary = typeof payload?.reasoning_summary === "string" ? payload.reasoning_summary : null;
  return {
    whyItMatters,
    topicTags,
    signalType,
    reasoningSummary
  };
}

export function getTrendSourceLabel(sourceType: string): string {
  switch (sourceType) {
    case "HN":
      return "Hacker News";
    case "GITHUB":
      return "GitHub";
    default:
      return sourceType;
  }
}

export function getTrendStatusLabel(status: string): string {
  switch (status) {
    case "ANALYZED":
      return "可处理";
    case "INGESTED":
      return "待分析";
    case "IGNORED":
      return "已忽略";
    case "SAVED_AS_NOTE":
      return "已转 Note";
    case "PROMOTED_TO_IDEA":
      return "已转 Idea";
    default:
      return status;
  }
}

export function formatTrendScore(score: number): string {
  if (Number.isFinite(score)) {
    return score.toFixed(2);
  }
  return "NA";
}

export function getSuggestedActionLabel(value: string | null): string {
  switch (value) {
    case "IGNORE":
      return "建议：忽略";
    case "SAVE_AS_NOTE":
      return "建议：转 Note";
    case "PROMOTE_TO_IDEA":
      return "建议：转 Idea";
    case null:
    case undefined:
      return "建议：未知";
    default:
      return `建议：${value}`;
  }
}

export function isIgnoreActionEnabled(status: string): { enabled: boolean; reason: string | null } {
  if (status === "ANALYZED") {
    return { enabled: true, reason: null };
  }
  return { enabled: false, reason: "仅支持对 ANALYZED 条目执行 IGNORE。" };
}

export function isSaveAsNoteActionEnabled(status: string): { enabled: boolean; reason: string | null } {
  if (status === "ANALYZED") {
    return { enabled: true, reason: null };
  }
  return { enabled: false, reason: "仅支持对 ANALYZED 条目执行 SAVE_AS_NOTE。" };
}

function formatTrendDateTime(value: string | null | undefined): string {
  if (!value) {
    return "未知";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return "未知";
  }
  return parsed.toLocaleString("zh-CN");
}

export function toTrendInboxCardModel(item: TrendInboxItem): TrendInboxCardModel {
  const analysis = normalizeTrendAnalysisPayload(item.analysis_payload);
  const ignore = isIgnoreActionEnabled(item.status);
  const saveAsNote = isSaveAsNoteActionEnabled(item.status);
  const updatedAtLabel = formatTrendDateTime(item.updated_at);
  const suggestedActionLabel = getSuggestedActionLabel(item.suggested_action);
  const actions: TrendInboxActionModel[] = [
    {
      key: "IGNORE",
      label: "Ignore",
      isEnabled: ignore.enabled,
      disabledReason: ignore.reason
    },
    {
      key: "SAVE_AS_NOTE",
      label: "Save as Note",
      isEnabled: saveAsNote.enabled,
      disabledReason: saveAsNote.reason
    },
    {
      key: "PROMOTE_TO_IDEA",
      label: "Promote to Idea",
      isEnabled: false,
      disabledReason: DEFAULT_DISABLED_REASON
    }
  ];

  return {
    trendItemId: item.trend_item_id,
    title: item.title,
    url: item.url,
    summary: item.summary,
    sourceType: item.source_type,
    sourceLabel: getTrendSourceLabel(item.source_type),
    scoreLabel: formatTrendScore(item.normalized_score),
    status: item.status,
    statusLabel: getTrendStatusLabel(item.status),
    suggestedAction: item.suggested_action,
    suggestedActionLabel,
    whyItMatters: analysis.whyItMatters,
    topicTags: analysis.topicTags,
    signalType: analysis.signalType,
    reasoningSummary: analysis.reasoningSummary,
    updatedAtLabel,
    actions
  };
}

export function buildTrendInboxCardModels(items: TrendInboxItem[]): TrendInboxCardModel[] {
  return (items ?? []).filter(Boolean).map(toTrendInboxCardModel);
}
