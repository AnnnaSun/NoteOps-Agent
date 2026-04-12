import test from "node:test";
import assert from "node:assert/strict";

import {
  NAVIGATION_HASH_BY_VIEW,
  parseNavigationHash,
  toNavigationHash
} from "../src/navigationState.ts";

test("parseNavigationHash maps supported hashes to main views", () => {
  assert.equal(parseNavigationHash("#/"), "HOME");
  assert.equal(parseNavigationHash("#/notes"), "NOTES");
  assert.equal(parseNavigationHash("#/ideas"), "IDEAS");
  assert.equal(parseNavigationHash("#/workspace"), "WORKSPACE");
  assert.equal(parseNavigationHash("#/trends"), "TRENDS");
});

test("parseNavigationHash falls back to HOME for unknown or empty hashes", () => {
  assert.equal(parseNavigationHash(""), "HOME");
  assert.equal(parseNavigationHash("#/unknown"), "HOME");
  assert.equal(parseNavigationHash(undefined), "HOME");
});

test("toNavigationHash returns the canonical hash for each main view", () => {
  assert.equal(toNavigationHash("HOME"), "#/");
  assert.equal(toNavigationHash("NOTES"), "#/notes");
  assert.equal(toNavigationHash("IDEAS"), "#/ideas");
  assert.equal(toNavigationHash("WORKSPACE"), "#/workspace");
  assert.equal(toNavigationHash("TRENDS"), "#/trends");
  assert.deepEqual(NAVIGATION_HASH_BY_VIEW, {
    HOME: "#/",
    NOTES: "#/notes",
    IDEAS: "#/ideas",
    WORKSPACE: "#/workspace",
    TRENDS: "#/trends"
  });
});
