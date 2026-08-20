---
name: roottools-research
description: Research Android, OEM, root, Shizuku/Sui, and reference-project capabilities before RootTools implementation.
---

# RootTools Research

Use this skill when a task starts with 调研 / research / compare / reference project / Android or OEM behavior investigation.

## Workflow

1. Read `AGENTS.md`, the relevant `docs/*.md`, and `docs/templates/research-plan.md`.
2. Inspect current RootTools code before proposing a new implementation.
3. Identify the existing source of truth: Repository, Controller, PrivilegeRouter, RootShell, or model.
4. Separate evidence into:
   - Android/AOSP contract;
   - OEM/device evidence;
   - open-source/reference-project evidence;
   - inference or recommendation.
5. For privileged behavior, record required capability, backend, risk, rollback, and unsupported cases.
6. Write findings into the canonical feature document. Do not create a parallel document when one already exists.
7. End with a prioritized gap list and acceptance criteria that implementation can test.

## Output contract

Research is incomplete without current-state evidence, architectural impact, safety impact, test strategy, and unresolved assumptions.
