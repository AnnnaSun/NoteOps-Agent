import type { ReactNode } from "react";

import type { HomeSummaryCard } from "../homeState";
import type { MainView } from "../navigationState";

type HomeEntryCard = {
  view: MainView;
  title: string;
  description: string;
  meta: string;
  actionLabel: string;
  onOpen: () => void;
};

type HomeViewProps = {
  summaryCards: HomeSummaryCard[];
  entryCards: HomeEntryCard[];
  capturePanel: ReactNode;
};

export function HomeView({ summaryCards, entryCards, capturePanel }: HomeViewProps) {
  return (
    <div className="view-stack">
      <section className="panel panel-hero-lite">
        <div className="panel-heading">
          <div>
            <p className="panel-kicker">Home</p>
            <h2>Capture-first 首页</h2>
          </div>
        </div>
        <p className="hero-copy">
          Home 只保留总览、主入口和 Capture 启动区，不再承载 Notes / Ideas / Workspace 的主体列表。
        </p>
      </section>

      <section className="home-summary-grid" aria-label="首页概览">
        {summaryCards.map((card) => (
          <article key={card.key} className="overview-card">
            <span className="overview-label">{card.label}</span>
            <strong>{card.value}</strong>
            <p>{card.description}</p>
          </article>
        ))}
      </section>

      <section className="home-entry-grid" aria-label="主视图入口">
        {entryCards.map((card) => (
          <article key={card.view} className="panel entry-card">
            <div className="panel-heading compact">
              <div>
                <p className="panel-kicker">{card.title}</p>
                <h3>{card.meta}</h3>
              </div>
            </div>
            <p>{card.description}</p>
            <div className="form-actions">
              <button type="button" className="ghost-button" onClick={card.onOpen}>
                {card.actionLabel}
              </button>
            </div>
          </article>
        ))}
      </section>

      {capturePanel}
    </div>
  );
}
