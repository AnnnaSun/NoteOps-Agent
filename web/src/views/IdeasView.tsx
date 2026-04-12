import type { ReactNode } from "react";

type IdeasViewProps = {
  content: ReactNode;
};

export function IdeasView({ content }: IdeasViewProps) {
  return <div className="view-stack">{content}</div>;
}
