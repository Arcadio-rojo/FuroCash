# FuroCash Banking Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Extend the existing app with the approved mobile banking demo features while preserving auth/data/branding.
**Architecture:** Keep React/Javalin. Separate responsive navigation/screens, JDBC demo wallet operations, cards/profile services and QR codecs. Share existing auth, balance and history.
**Tech Stack:** React 19, TypeScript 6, Vite 8; Java 21, Javalin 6.7, JDBC, PostgreSQL, Flyway.
**Spec:** docs/superpowers/specs/2026-10-19-banking-demo-redesign.md

## Global constraints
- No Angular/Spring migration unless the user explicitly selects it after the discrepancy notice.
- No real payment integration, production card credentials or SMS tests.
- Preserve existing user data and V1?V3 migrations; add V4 only.
- Work in the provided workspace; managed .git is read-only. Leave changes uncommitted.
- Keep branding/auth implementation and PhilSMS secrets unchanged.

## Review focus
- Same idempotency key with changed body must fail and concurrent duplicates cannot move money twice.
- QR metadata is untrusted; persisted ownership, amount, expiration and consumption are authoritative.
- Cards cannot bypass owner/status/daily limit or create a second balance.
- Failed mutations roll back both money/history and idempotency state.
- Navigation, long names and amount fields cannot cause horizontal overflow or be covered by mobile navigation.

### Task 1: Baseline contracts and wallet persistence (root)
Files: new V4 migration, Receipt/Qr DTOs, IdempotencyService, DemoWalletService, DemoRoutes, AccountRoutes/AuthFilter/Main wiring; integration test support and WalletDemoIntegrationTest.
Interfaces: exact API and receipt/QR contracts in spec; public IdempotencyService.execute(long userId,String key,String operation,Object payload,SqlWork<Receipt>) with callback Connection.
- [ ] Write endpoint/idempotency tests; run red against existing routes.
- [ ] Implement additive schema, prepared SQL, locks, two-stage deposits, recipient lookup, transfer receipt and persisted QR verification.
- [ ] Require idempotency headers on money endpoints; update current API client/tests while preserving endpoint paths and balance field.
- [ ] Run isolated wallet tests green.

### Task 2: Responsive frontend (frontend agent)
Files: frontend only, including api.ts, App.tsx, shared shell/menu/icons/dialog/receipt/QR components, Home/Transactions/Deposit/Transfer/ScanPay/Card/Profile pages, styles and tests.
Consumes shared API contracts above. Use existing AuthProvider and PIN screens. Minimal QR dependencies qrcode and qr-scanner plus qrcode types. No replacement of login/registration styles or logo.
- [ ] Write route/QR/form regressions and implement shared navigation plus balance actions first.
- [ ] Add deposit/transfer confirmation and receipt flows, QR camera/image handling, card controls and profile editing.
- [ ] Keep request keys stable through retries; guard double submission.
- [ ] Test build/lint/QR codec and screen flows; verify all target CSS breakpoints.

### Task 3: Cards and profile backend (backend agent)
Files: CardService/CardRoutes/ProfileService/ProfileRoutes, User/UserDao/UserResponse extensions, CardResponse DTO and CardProfileIntegrationTest. Do not edit migration/Main/AuthFilter/IdempotencyService owned by root.
Consumes V4 demo_cards and transaction card_id columns and shared IdempotencyService/Receipt. CardService uses account balance, row locks and daily spend from transaction history.
- [ ] Write owner/status/limit/profile tests first.
- [ ] Implement card creation/ownership/status/limit, simulated purchase and history; profile update/read.
- [ ] Reuse existing PIN recovery and never expose PIN hash or token.
- [ ] Run isolated tests after root contract implementation is ready.

### Task 4: Integration, review and handoff (root/reviewer)
- [ ] Wire card/profile routes and consolidate API integration.
- [ ] Run all frontend tests/build/lint and full Java/PostgreSQL suite sequentially after frontend output is stable.
- [ ] Independent source review; fix important defects with regression tests.
- [ ] Update README/API/feature inventory, dependency/migration notes and exact startup instructions.
- [ ] Report tested features and remaining camera/browser or framework limitations honestly.
