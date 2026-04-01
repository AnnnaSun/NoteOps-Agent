# Codex Agent Stack Policy

## 1. Goal

This repository may contain multiple installed skills, rules, and agent packs.
Their roles are different. Follow this priority order to avoid conflicts, prompt bloat, and inconsistent execution.

## 2. Priority Order

When multiple rule sources exist, apply them in this order:

1. Repository project documents and local constraints
2. This file
3. Project-specific implementation plans and prompts
4. Default engineering rules from the installed baseline pack
5. Task-specific skills invoked for the current task
6. Review-oriented skills used only after implementation

If two instructions conflict, the higher-priority source wins.

## 3. Repository-First Rule

Always read and follow repository documents before making changes.

Prioritize these repository-local files when they exist:

- README.md
- PRD.md / docs/PRD.md
- Plan.md
- Prompt.md
- Documentation.md
- agent.md
- implement.md
- architecture docs
- API contracts
- migration notes
- test guides

Do not let external skill packs override repository business goals, module boundaries, naming conventions, or acceptance criteria.

## 4. Installed Tooling Roles

Use installed packs by role, not as equal co-owners of the task.

### 4.1 Baseline engineering pack
Use as the default development behavior layer:
- understand existing code before editing
- prefer minimal, controlled changes
- preserve consistency with current architecture
- include validation, tests, and safety checks
- consider security and edge cases

### 4.2 General-purpose skill packs
Use only when the task benefits from structured assistance such as:
- planning
- decomposition
- refactoring
- documentation
- research synthesis
- structured analysis

Do not allow general-purpose skills to dominate simple implementation tasks.

### 4.3 UI/UX specialist packs
Use only for frontend or interaction-heavy work such as:
- page layout
- component design
- interaction flow
- dashboard/list/detail/form pages
- design-to-code refinement
- usability improvements

Do not use UI/UX specialist skills for backend-only tasks, database modeling, or service-layer logic.

### 4.4 Review specialist packs
Use only after implementation or during explicit review tasks.
Focus on:
- SOLID
- separation of concerns
- security risks
- boundary conditions
- error handling
- performance risks
- maintainability

Do not let review packs control first-pass implementation unless the task is explicitly a review task.

## 5. Task Routing Rules

Route tasks to the correct capability set.

### Backend task
Use:
- repository docs
- baseline engineering rules
- task-specific general skills only if needed

Avoid:
- UI/UX specialist packs

### Frontend/UI task
Use:
- repository docs
- UI/UX specialist pack
- baseline engineering rules

Use review packs only after the implementation pass.

### Architecture/planning task
Use:
- repository docs
- general-purpose planning/decomposition skills
- baseline engineering rules

Do not start coding before identifying:
- affected modules
- data flow
- API impact
- persistence impact
- acceptance checks

### Review/refactor task
Use:
- review specialist pack
- repository acceptance criteria
- baseline engineering rules

Return:
- findings
- severity
- minimal patch plan
- regression risks

## 6. Execution Discipline

Before coding:

1. Identify the task type
2. Read the repository docs
3. Check which installed pack is actually relevant
4. Use only the minimum necessary specialist layer
5. Keep changes scoped to the requested goal

Do not activate multiple specialist modes unless the task genuinely crosses domains.

Examples:
- backend API + DB change: no UI/UX pack
- page redesign: UI/UX pack allowed
- final code quality pass: review pack allowed
- docs cleanup: general-purpose writing/doc skill allowed

## 7. Implementation Rules

When implementing code:

- prefer existing patterns over inventing new abstractions
- keep file and module structure consistent
- avoid broad refactors unless required
- do not silently change public contracts
- do not introduce hidden coupling
- handle validation, logging, and error paths explicitly
- update related tests and docs when behavior changes
- explain tradeoffs when multiple valid options exist

For incomplete context, inspect the existing codebase before proposing a new structure.

## 8. Review Rules

When reviewing code:

- classify issues by severity: critical / major / minor
- separate correctness, security, maintainability, and performance concerns
- point to concrete failure modes
- prefer minimal corrective patches
- do not request unnecessary rewrites
- highlight missing tests and missing edge-case handling

## 9. Conflict Resolution

If installed skills conflict:

- follow repository docs first
- then follow this file
- then follow the task-specific role assignment
- ignore irrelevant specialist instructions

If a specialist pack tries to expand scope beyond the user’s request, constrain it back to the requested task.

## 10. Output Expectations

For implementation tasks, provide:

- concise plan
- affected files
- code changes
- verification steps
- known risks or assumptions

For review tasks, provide:

- findings by severity
- affected files/modules
- suggested patches
- regression checklist

For planning tasks, provide:

- scope
- dependencies
- step breakdown
- acceptance criteria

## 11. Scope Control

Do not turn a small task into a full-system redesign.

Reject unnecessary expansion such as:
- replacing working architecture without cause
- introducing new frameworks without justification
- rewriting unrelated modules
- mixing UI redesign into backend tasks
- mixing review ceremony into straightforward implementation

## 12. Repository-Specific Override Section

Project maintainers may customize this section.

Example:

- backend framework:
- frontend framework:
- database:
- coding language priority:
- test strategy:
- deployment target:
- module boundaries:
- AI integration constraints:
- documentation locations:
- acceptance standard:

This section overrides generic skill behavior when filled.

## 13. Default Working Mode

Unless explicitly stated otherwise:

- use baseline engineering behavior by default
- invoke general-purpose skills only when complexity justifies it
- invoke UI/UX specialist skills only for frontend work
- invoke review specialist skills only after implementation
- keep repository-local documents as the source of truth

## 14. UI/UX skill file output policy

UI/UX skills are allowed to read their own internal datasets and produce design recommendations,
but they must not copy, generate, export, vendor, or persist CSV/JSON/YAML dataset files into this repository.

Forbidden outputs:
- *.csv design catalogs
- copied skill data files
- palette databases
- typography databases
- style lookup tables
- generated asset inventories

Allowed outputs:
- code changes for the requested page/component
- concise markdown design notes when explicitly requested
- small repository-local docs only when needed

If a UI/UX skill needs structured reference data, keep it in memory during the task.
Do not write dataset files into the repo.