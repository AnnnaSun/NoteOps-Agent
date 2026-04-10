import test from "node:test";
import assert from "node:assert/strict";

import {
  foldBootstrapSlice,
  resolveIdeaSelection,
  shouldApplyIdeaActionFollowUp
} from "../src/ideaWorkspaceState.ts";

test("foldBootstrapSlice keeps successful panel data when Idea panel fails", () => {
  const notes = foldBootstrapSlice<string[]>(
    { status: "fulfilled", value: ["note-1"] },
    []
  );
  const ideas = foldBootstrapSlice<string[]>(
    { status: "rejected", reason: new Error("idea list failed") },
    []
  );

  assert.deepEqual(notes, {
    data: ["note-1"],
    error: null
  });
  assert.deepEqual(ideas, {
    data: [],
    error: "idea list failed"
  });
});

test("resolveIdeaSelection preserves current selection when refresh finishes after user switched Idea", () => {
  const nextSelection = resolveIdeaSelection(
    [
      { id: "idea-a" },
      { id: "idea-b" }
    ],
    "idea-a",
    "idea-b",
    true
  );

  assert.equal(nextSelection, "idea-b");
});

test("shouldApplyIdeaActionFollowUp only updates detail when the acted-on Idea is still selected", () => {
  assert.equal(shouldApplyIdeaActionFollowUp("idea-a", "idea-a"), true);
  assert.equal(shouldApplyIdeaActionFollowUp("idea-a", "idea-b"), false);
});
