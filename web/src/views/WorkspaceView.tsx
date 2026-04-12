import type { ReactNode } from "react";

type WorkspaceViewProps = {
  content: ReactNode;
};

export function WorkspaceView({ content }: WorkspaceViewProps) {
  return <div className="view-stack">{content}</div>;
}
