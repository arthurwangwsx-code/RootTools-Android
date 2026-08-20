---
name: roottools-review
description: Review RootTools changes for architecture, privileged safety, injection risk, i18n, tests, and regression.
---

# RootTools Review

Review in this order:

1. **Safety:** destructive behavior, protected targets, rollback, audit.
2. **Privilege boundary:** typed Controller/Repository and semantic backend routing.
3. **Input security:** package/component/AppOp/token/Intent validation and injection resistance.
4. **Architecture:** one truth source, no feature-to-feature private dependency, no new giant file.
5. **Lifecycle/performance:** polling, Binder/session lifetime, foreground/background behavior.
6. **i18n/accessibility:** no new UI literals, content descriptions, locale-safe formatting.
7. **Tests:** policy/parser JVM coverage plus appropriate device validation.
8. **Docs:** canonical design and ledger reflect actual behavior.

Classify findings by severity and cite exact files/lines when possible.
