import test from "node:test";
import assert from "node:assert/strict";

import {
  buildTrendInboxCardModels,
  getSuggestedActionLabel,
  getTrendSourceLabel,
  isIgnoreActionEnabled,
  isSaveAsNoteActionEnabled,
  normalizeTrendAnalysisPayload,
  toTrendInboxCardModel
} from "../src/trendInboxState.ts";
import type { TrendInboxItem } from "../src/types.ts";

function baseTrendItem(overrides: Partial<TrendInboxItem> = {}): TrendInboxItem {
  return {
    trend_item_id: "trend-1",
    user_id: "user-1",
    source_type: "HN",
    source_item_key: "hn:1",
    title: "A thing happened",
    url: "https://example.com",
    summary: "Short summary",
    normalized_score: 0.82,
    status: "ANALYZED",
    suggested_action: "SAVE_AS_NOTE",
    analysis_payload: {
      why_it_matters: "Because it changes workflows",
      topic_tags: ["agents", "tools"],
      signal_type: "product",
      reasoning_summary: "Signal looks durable"
    },
    source_published_at: null,
    last_ingested_at: null,
    updated_at: "2026-04-11T09:00:00Z",
    ...overrides
  };
}

test("normalizeTrendAnalysisPayload reads the stable fields used by the UI", () => {
  assert.deepEqual(
    normalizeTrendAnalysisPayload({
      why_it_matters: "x",
      topic_tags: ["a", "b"],
      signal_type: "s",
      reasoning_summary: "r"
    }),
    {
      whyItMatters: "x",
      topicTags: ["a", "b"],
      signalType: "s",
      reasoningSummary: "r"
    }
  );
});

test("getTrendSourceLabel maps known source types", () => {
  assert.equal(getTrendSourceLabel("HN"), "Hacker News");
  assert.equal(getTrendSourceLabel("GITHUB"), "GitHub");
  assert.equal(getTrendSourceLabel("OTHER"), "OTHER");
});

test("getSuggestedActionLabel renders a stable hint even for null", () => {
  assert.equal(getSuggestedActionLabel("IGNORE"), "建议：忽略");
  assert.equal(getSuggestedActionLabel("SAVE_AS_NOTE"), "建议：转 Note");
  assert.equal(getSuggestedActionLabel("PROMOTE_TO_IDEA"), "建议：转 Idea");
  assert.equal(getSuggestedActionLabel(null), "建议：未知");
});

test("isIgnoreActionEnabled only enables when status is ANALYZED", () => {
  assert.deepEqual(isIgnoreActionEnabled("ANALYZED"), { enabled: true, reason: null });
  assert.deepEqual(isIgnoreActionEnabled("INGESTED"), { enabled: false, reason: "仅支持对 ANALYZED 条目执行 IGNORE。" });
});

test("isSaveAsNoteActionEnabled only enables when status is ANALYZED", () => {
  assert.deepEqual(isSaveAsNoteActionEnabled("ANALYZED"), { enabled: true, reason: null });
  assert.deepEqual(isSaveAsNoteActionEnabled("INGESTED"), { enabled: false, reason: "仅支持对 ANALYZED 条目执行 SAVE_AS_NOTE。" });
});

test("toTrendInboxCardModel exposes enabled IGNORE and disabled conversion actions", () => {
  const model = toTrendInboxCardModel(baseTrendItem());
  const ignore = model.actions.find((action) => action.key === "IGNORE");
  const save = model.actions.find((action) => action.key === "SAVE_AS_NOTE");
  const promote = model.actions.find((action) => action.key === "PROMOTE_TO_IDEA");

  assert.equal(model.sourceLabel, "Hacker News");
  assert.equal(model.scoreLabel, "0.82");

  assert.equal(ignore?.isEnabled, true);
  assert.equal(ignore?.disabledReason, null);
  assert.equal(save?.isEnabled, true);
  assert.equal(save?.disabledReason, null);
  assert.equal(promote?.isEnabled, false);
  assert.ok(promote?.disabledReason?.includes("Step 4.6"));
});

test("buildTrendInboxCardModels drops nullish entries and maps items", () => {
  const items = [baseTrendItem({ trend_item_id: "t1" }), baseTrendItem({ trend_item_id: "t2" })];
  const models = buildTrendInboxCardModels(items);
  assert.equal(models.length, 2);
  assert.equal(models[0]?.trendItemId, "t1");
  assert.equal(models[1]?.trendItemId, "t2");
});
