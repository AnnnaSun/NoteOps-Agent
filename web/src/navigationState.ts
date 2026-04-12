export type MainView = "HOME" | "NOTES" | "IDEAS" | "WORKSPACE" | "TRENDS";

export const NAVIGATION_HASH_BY_VIEW: Record<MainView, string> = {
  HOME: "#/",
  NOTES: "#/notes",
  IDEAS: "#/ideas",
  WORKSPACE: "#/workspace",
  TRENDS: "#/trends"
};

const VIEW_BY_HASH = new Map<string, MainView>(
  Object.entries(NAVIGATION_HASH_BY_VIEW).map(([view, hash]) => [hash, view as MainView])
);

export function parseNavigationHash(hash: string | null | undefined): MainView {
  if (!hash) {
    return "HOME";
  }
  return VIEW_BY_HASH.get(hash) ?? "HOME";
}

export function toNavigationHash(view: MainView): string {
  return NAVIGATION_HASH_BY_VIEW[view];
}
