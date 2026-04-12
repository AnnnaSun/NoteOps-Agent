import type { ReactNode } from "react";

type NotesViewProps = {
  searchPanel: ReactNode;
  notesPanel: ReactNode;
  detailPanel: ReactNode;
  isDetailVisible: boolean;
};

export function NotesView({ searchPanel, notesPanel, detailPanel, isDetailVisible }: NotesViewProps) {
  return (
    <div className={`workspace-grid ${isDetailVisible ? "detail-open" : "detail-closed"}`}>
      <div className="column-stack">
        {searchPanel}
        {notesPanel}
      </div>
      {detailPanel}
    </div>
  );
}
