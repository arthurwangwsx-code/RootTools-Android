# Feature 开发方案模板

## 1. Problem / User Story

- 用户场景：
- 现有问题：
- 成功标准：

## 2. Scope

### In scope

- 

### Out of scope

- 

## 3. Current State

- 已有 Repository / Controller：
- 已有 UI：
- 可复用能力：
- 技术债：

## 4. Capability / Permission

| Capability | Backend | Permission | Fallback | Failure UI |
|---|---|---|---|---|
| | | | | |

## 5. Architecture

```text
UI
 -> ViewModel
 -> Controller / Repository
 -> Policy / Validator
 -> Privilege Adapter
 -> Android / Linux
```

## 6. Domain Model / API

- Model：
- Query：
- Action：
- Result / Error：

## 7. Safety / Rollback

- 风险等级：
- 前值：
- 回滚：
- 是否二次确认：
- 是否可能远程失联：

## 8. UI / UX

- Screen：
- State：Loading / Ready / Empty / Error / Unavailable
- Shared components：
- i18n：
- Accessibility：

## 9. Test Matrix

- happy path：
- unavailable：
- invalid input：
- fallback：
- protected target：
- rollback：
- regression：
- real device：

## 10. Implementation Plan

1. contract + tests；
2. policy / parser；
3. backend；
4. controller/repository；
5. ViewModel；
6. UI；
7. docs；
8. validation。

## 11. Acceptance Criteria

- [ ] 

## 12. Open Questions

- 

