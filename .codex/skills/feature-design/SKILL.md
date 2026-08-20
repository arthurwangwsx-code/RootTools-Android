---
name: roottools-feature-design
description: Turn a RootTools capability request into an implementable feature contract with architecture, safety, tests, and staged delivery.
---

# RootTools Feature Design

1. Start from `docs/templates/feature-design.md`.
2. Define user scenarios and non-goals before UI details.
3. Reuse existing truth sources; explicitly list any new Repository / Controller / model that is actually required.
4. Define capability/backend routing using semantic operations, never arbitrary shell exposure.
5. Define read path and write path separately.
6. Assign risk level to every mutation and specify rollback/audit behavior.
7. Specify Android/OEM compatibility assumptions and device probes.
8. Specify JVM tests, integration checks, and Samsung real-device acceptance.
9. Split implementation into independently verifiable milestones.
10. Update `docs/09-delivery-ledger.md` when implementation is accepted for execution.

Do not introduce a Gradle module unless the module admission criteria in `docs/17-engineering-governance-and-ai-workflow.md` are met.
