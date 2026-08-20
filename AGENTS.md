# RootTools AI Engineering Rules

This file is the project-level operating contract for AI coding agents.

## 1. Mission

RootTools is a privileged Android toolbox. Correctness, rollback, device safety, and clear capability boundaries are more important than feature count or implementation speed.

Before changing code, preserve these invariants:

1. UI / Tile / Receiver / Widget never executes arbitrary privileged shell directly.
2. Privileged writes go through a typed Controller / Action boundary.
3. Framework operations prefer the semantic PrivilegeRouter route; root-only Linux operations stay in RootShell.
4. A system capability has one source of truth. Do not create a second implementation for a new screen.
5. Destructive actions require explicit risk treatment, auditability, and rollback information where technically possible.
6. Read-only collectors must not silently mutate device state.
7. Do not weaken Android, Magisk, Shizuku, Sui, or OEM safety prompts merely to make automation easier.

Read `docs/14-core-logic-testing-standard.md` before changing privileged logic.

## 2. Required task workflow

For every non-trivial task:

```text
inspect git status
  -> inspect relevant code + docs
  -> identify existing truth source
  -> define behavior / risks / acceptance criteria
  -> add or update pure tests first for core decisions
  -> implement smallest coherent change
  -> run targeted tests
  -> run project verification
  -> update relevant docs / ledger
  -> summarize changed files, validation, residual risk
```

Never overwrite, revert, stage, or commit unrelated working-tree changes.

Do not commit or push unless the task explicitly asks for it. When committing is requested, use atomic Conventional Commits as defined below.

## 3. Architecture boundaries

The project currently remains a single Gradle `:app` module, but source code must be logically modularized.

Target package shape:

```text
com.arthur.roottools
├── app/                    # composition root, navigation, Android entry points
├── core/
│   ├── model/
│   ├── root/
│   ├── privilege/
│   ├── safety/
│   ├── common/
│   └── ui/
└── feature/
    ├── dashboard/
    ├── performance/
    ├── adb/
    ├── startup/
    ├── apps/
    ├── diagnostics/
    ├── modules/
    ├── network/
    ├── storage/
    ├── battery/
    └── actions/
```

New code should move toward this shape without large unrelated migrations.

### File-size guardrails

These are project guardrails, not universal Android rules:

- a screen file over 600 lines is a refactor warning;
- a screen file over 900 lines must not receive another unrelated feature section;
- a ViewModel over 500 lines should split feature state / reducers / coordinators;
- reusable UI repeated 3 times becomes a shared component candidate;
- reusable non-UI behavior repeated 2 times becomes a shared core abstraction candidate;
- do not create a generic abstraction for a single call site only to reduce line count.

`DashboardScreen.kt` is legacy debt and should be progressively split by feature instead of expanded further.

## 4. Gradle modularization policy

Do not create one Gradle module per card.

Use three stages:

1. **Now:** logical packages + split giant files + shared UI components inside `:app`.
2. **Core extraction:** introduce a small number of stable modules only when the boundary is proven (`:core:model`, `:core:privilege`, `:core:designsystem` are candidates).
3. **Feature extraction:** create a feature module only when it has a real independent boundary, such as multiple screens, substantial code size, independent tests, or frequent parallel changes.

Before adding a module, document its API, dependency direction, reason, and what build/test isolation it buys.

## 5. UI and design-system rules

Do not add another one-off visual pattern when an equivalent component exists.

Shared component candidates include:

- `RootToolsScaffold`
- `ToolSectionCard`
- `MetricTile`
- `StatusChip`
- `BackendBadge`
- `CapabilityGate`
- `EmptyState`
- `LoadingState`
- `RiskConfirmDialog`
- `DangerActionButton`
- `ActionResultBanner`

Colors, spacing, radius, typography decisions, risk levels, and status semantics must come from theme/design tokens rather than local magic values when reused.

## 6. Internationalization

New user-visible strings must not be hard-coded in Kotlin.

Rules:

1. Use Android string resources and `stringResource` / resource IDs.
2. The default `values/strings.xml` must remain complete.
3. Target language structure is default English + `values-zh-rCN` Simplified Chinese.
4. Product names, shell commands, protocol constants, package names, and non-translatable identifiers use `translatable="false"` where appropriate.
5. Use formatted string resources, plurals, and locale-aware formatting instead of manual sentence concatenation.
6. `contentDescription`, error messages, confirmations, empty states, and accessibility labels are also user-visible strings.

Legacy hard-coded strings should be migrated feature-by-feature; do not mix a whole-app localization rewrite into unrelated feature work.

## 7. Testing contract

At minimum, privileged or policy-changing work must cover:

- happy path;
- capability unavailable;
- invalid / hostile input;
- fallback behavior;
- protected target;
- boundary values;
- backend attribution;
- rollback / previous value when applicable;
- regression of the existing route.

Pure decisions belong in JVM tests. Android / Binder / shell / OEM behavior belongs in integration or real-device validation.

Project verification baseline:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Use `bash scripts/build.sh` for the full local delivery gate when practical.

## 8. Commit convention

Use Conventional Commits:

```text
feat(adb): add native wireless status parser
fix(privilege): reject malformed component names
refactor(ui): split battery screen from dashboard
test(policy): cover protected package behavior
docs(architecture): define module extraction thresholds
build(deps): centralize dependency versions
ci(verify): add pull request quality gate
chore(repo): ignore local reference projects
```

Allowed common types: `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`, `ci`, `chore`.

Prefer project scopes such as `adb`, `apps`, `battery`, `dashboard`, `diagnostics`, `modules`, `network`, `performance`, `privilege`, `root`, `startup`, `storage`, `ui`, `docs`.

One commit should represent one coherent reason to change. Do not combine formatting, broad refactors, product behavior, and unrelated documentation in one commit.

Before creating a commit, validate the subject with `python3 scripts/commit_guard.py --subject "<subject>"`.
Local clones can enable the versioned hook with `bash scripts/setup-dev.sh`.

## 9. Documentation contract

Use `docs/` as the long-term project memory.

For a new feature or major change, documentation should answer:

1. problem and user scenario;
2. current-state evidence;
3. capability and permission requirements;
4. architecture / data flow;
5. safety and rollback;
6. OEM / Android compatibility assumptions;
7. test matrix;
8. phased implementation plan;
9. acceptance criteria;
10. unresolved questions.

Use the templates under `docs/templates/` for research, feature design, ADRs, and validation reports.

## 10. AI skill routing

Use the project skills under `.codex/skills/` when the task matches. The detailed catalog and rationale are in `docs/17-engineering-governance-and-ai-workflow.md`.

- Research: `.codex/skills/research/SKILL.md`
- Feature design: `.codex/skills/feature-design/SKILL.md`
- Implementation: `.codex/skills/implementation/SKILL.md`
- Refactor: `.codex/skills/refactor/SKILL.md`
- Review: `.codex/skills/review/SKILL.md`
- Documentation: `.codex/skills/documentation/SKILL.md`
- Device validation: `.codex/skills/device-validation/SKILL.md`
- Release: `.codex/skills/release/SKILL.md`

