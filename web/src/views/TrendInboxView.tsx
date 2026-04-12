import { useId } from "react";

import type { TrendInboxFilters } from "../trendInboxState";
import {
  buildTrendInboxCardModels,
  TREND_INBOX_SOURCE_OPTIONS,
  TREND_INBOX_STATUS_OPTIONS
} from "../trendInboxState";
import type { TrendInboxItem } from "../types";

type TrendInboxViewProps = {
  items: TrendInboxItem[];
  isLoading: boolean;
  error: string | null;
  actionNotice: string | null;
  actionErrorsById: Record<string, string | null>;
  actionLoadingById: Record<string, boolean>;
  filters: TrendInboxFilters;
  onChangeFilters: (next: TrendInboxFilters) => void;
  onIgnore: (trendItemId: string) => void;
  onSaveAsNote: (trendItemId: string) => void;
};

export function TrendInboxView({
  items,
  isLoading,
  error,
  actionNotice,
  actionErrorsById,
  actionLoadingById,
  filters,
  onChangeFilters,
  onIgnore,
  onSaveAsNote
}: TrendInboxViewProps) {
  const statusSelectId = useId();
  const sourceSelectId = useId();
  const cards = buildTrendInboxCardModels(items);
  const shouldShowEmpty = !isLoading && !error && cards.length === 0;

  return (
    <div className="view-stack">
      <section className="panel panel-hero-lite trend-inbox-hero">
        <div className="panel-heading">
          <div>
            <p className="panel-kicker">Trends</p>
            <h2>Trend Inbox</h2>
          </div>
          <span className="meta-chip">{cards.length}</span>
        </div>
        <p className="hero-copy">
          这里展示已进入 Inbox 的趋势候选。`IGNORE` 与 `Save as Note` 现在都是真实动作，`Promote to Idea` 仍在 Step 4.6B。
        </p>

        <div className="trend-inbox-toolbar" aria-label="Trend Inbox filters">
          <label className="trend-inbox-filter">
            <span className="detail-label" id={`${statusSelectId}-label`}>
              Status
            </span>
            <select
              id={statusSelectId}
              aria-labelledby={`${statusSelectId}-label`}
              value={filters.status}
              onChange={(event) => {
                onChangeFilters({
                  ...filters,
                  status: event.target.value
                });
              }}
            >
              {TREND_INBOX_STATUS_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>

          <label className="trend-inbox-filter">
            <span className="detail-label" id={`${sourceSelectId}-label`}>
              Source
            </span>
            <select
              id={sourceSelectId}
              aria-labelledby={`${sourceSelectId}-label`}
              value={filters.sourceType}
              onChange={(event) => {
                onChangeFilters({
                  ...filters,
                  sourceType: event.target.value
                });
              }}
            >
              {TREND_INBOX_SOURCE_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
        </div>

        {actionNotice ? <p className="status-message trend-inbox-notice">{actionNotice}</p> : null}
      </section>

      {error ? (
        <section className="panel">
          <div className="panel-heading compact">
            <div>
              <p className="panel-kicker">Trend Inbox</p>
              <h3>列表加载失败</h3>
            </div>
          </div>
          <p className="status-message error">{error}</p>
        </section>
      ) : null}

      {isLoading ? (
        <section className="panel">
          <div className="panel-heading compact">
            <div>
              <p className="panel-kicker">Trend Inbox</p>
              <h3>加载中</h3>
            </div>
          </div>
          <p className="status-message">正在拉取最新趋势候选。</p>
        </section>
      ) : null}

      {shouldShowEmpty ? (
        <section className="panel">
          <div className="panel-heading compact">
            <div>
              <p className="panel-kicker">Trend Inbox</p>
              <h3>空</h3>
            </div>
          </div>
          <p className="status-message">当前筛选条件下没有条目。</p>
        </section>
      ) : null}

      <section className="trend-inbox-list" aria-label="Trend Inbox list">
        {cards.map((card) => {
          const ignoreAction = card.actions.find((action) => action.key === "IGNORE");
          const saveAction = card.actions.find((action) => action.key === "SAVE_AS_NOTE");
          const isActionBusy = actionLoadingById[card.trendItemId] ?? false;
          const ignoreDisabledReason = ignoreAction
            ? ignoreAction.isEnabled
              ? null
              : ignoreAction.disabledReason
            : "IGNORE action 未配置。";

          return (
            <article key={card.trendItemId} className="panel trend-inbox-card">
              <div className="trend-inbox-card-header">
                <div className="trend-inbox-card-title">
                  <p className="panel-kicker">{card.sourceLabel}</p>
                  <h3 className="trend-inbox-title">
                    <a href={card.url} target="_blank" rel="noreferrer">
                      {card.title}
                    </a>
                  </h3>
                </div>
                <div className="trend-inbox-meta">
                  <span className="meta-chip">{card.statusLabel}</span>
                  <span className="meta-chip">Score {card.scoreLabel}</span>
                </div>
              </div>

              <p className="trend-inbox-suggested">{card.suggestedActionLabel}</p>

              <p className="trend-inbox-summary">{card.summary}</p>

              {card.whyItMatters ? <p className="trend-inbox-why">{card.whyItMatters}</p> : null}

              {card.topicTags.length ? (
                <div className="trend-inbox-tags" aria-label="Topic tags">
                  {card.topicTags.map((tag) => (
                    <span key={tag} className="meta-chip">
                      {tag}
                    </span>
                  ))}
                </div>
              ) : null}

              {card.signalType || card.reasoningSummary ? (
                <details className="trend-inbox-details">
                  <summary>更多</summary>
                  <div className="trend-inbox-details-body">
                    {card.signalType ? (
                      <p className="status-message">
                        <strong>Signal</strong>: {card.signalType}
                      </p>
                    ) : null}
                    {card.reasoningSummary ? (
                      <p className="status-message">
                        <strong>Reasoning</strong>: {card.reasoningSummary}
                      </p>
                    ) : null}
                    <p className="status-message">
                      <strong>Updated</strong>: {card.updatedAtLabel}
                    </p>
                  </div>
                </details>
              ) : (
                <p className="status-message">
                  <strong>Updated</strong>: {card.updatedAtLabel}
                </p>
              )}

              <div className="trend-inbox-actions" aria-label="Trend actions">
                <button
                  type="button"
                  className={`ghost-button ${isActionBusy ? "is-loading" : ""}`}
                  disabled={isActionBusy || Boolean(ignoreDisabledReason)}
                  title={ignoreDisabledReason ?? undefined}
                  onClick={() => onIgnore(card.trendItemId)}
                >
                  {isActionBusy ? <span className="button-spinner" aria-hidden="true" /> : null}
                  Ignore
                </button>
                <button
                  type="button"
                  className={`ghost-button ${isActionBusy ? "is-loading" : ""}`}
                  disabled={isActionBusy || !saveAction?.isEnabled}
                  title={saveAction?.disabledReason ?? undefined}
                  onClick={() => onSaveAsNote(card.trendItemId)}
                >
                  {isActionBusy ? <span className="button-spinner" aria-hidden="true" /> : null}
                  Save as Note
                </button>
                <button type="button" disabled title="Step 4.6B">
                  Promote to Idea
                </button>
              </div>
              {actionErrorsById[card.trendItemId] ? (
                <p className="status-message error trend-inbox-action-error">{actionErrorsById[card.trendItemId]}</p>
              ) : null}
              <p className="trend-inbox-action-hint">`Save as Note` 已可用，`Promote to Idea` 仍在 Step 4.6B。</p>
            </article>
          );
        })}
      </section>
    </div>
  );
}
