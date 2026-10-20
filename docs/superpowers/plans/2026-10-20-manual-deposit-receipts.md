# Manual deposits, reviewed transfers, and receipts

> Execute incrementally in the existing workspace using the subagent-driven-development workflow. The user's detailed feature specification is the authority; no framework migration, resets, real payments, or unrelated rewrites.

## Inspected baseline
React/TypeScript/Vite; Java21 Javalin/JDBC/PostgreSQL/Flyway V1–V4. DemoWalletService already handles prepare/complete deposits and locked/idempotent transfers. Receipt has nine legacy fields, no fee/recipient-account metadata; history cannot reopen receipts. Existing GCash demo and custom QR cards stay in their current order and location. Existing light/dark mode and request loading stay.

## Contracts
- POST `/api/deposits/demo` remains preparation. Accept legacy GCASH_DEMO and new CASH_DEMO, BANK_TRANSFER_DEMO, EWALLET_DEMO, OTHER_DEMO plus optional note150. New manual amounts bounded by DEMO_MANUAL_DEPOSIT_MAX (default50000, min0.01). Existing complete route handles both, with method/notes preserved. No funding until explicit completion.
- GET `/api/deposits/options` authenticated: `{enabled,minAmount,manualMaxAmount,fee,methods:[{value,label}]}`; numeric amounts, server-owned zero fee policy. Disabled-by-default flag preserved; no .env modifications in this task.
- POST `/api/transfers/review` accepts existing TransferInput; returns `{reference,recipient,amount,fee,total,balance,note,qrReference,expiresAt}`. Persist a five-minute reviewed snapshot. Receiver name/amount/note/QR/fee are authoritative; confirm revalidates under locks. Changed/expired review returns409; old clients without a review remain supported. Existing POST `/api/transfers` accepts additional `reviewReference`.
- Extend Receipt legacy9 fields with nullable `{fee,total,method,senderAccount,senderName,recipientAccount,recipientName}`. Preserve a nine-argument Java constructor for legacy card callers. Amounts JSON numbers. `total` means total credit/debit for its kind. All timestamps come from inserted database transaction rows. New receipts persisted atomically per owner+reference, immutable. Transfer receiver receipt kind TRANSFER_IN and balance reflects receiver's own transaction. No success unless status COMPLETED.
- GET `/api/transactions/{reference}/receipt` authenticated owner lookup;404 when unavailable. TransactionResponse/model/DAO add `receiptAvailable` boolean, preserving old constructors used by cards. Never synthesize unsupported historical receipts or mutate existing history.
- GET `/api/operations/{key}` authenticated returns `{state:"COMPLETED"|"PROCESSING"|"NOT_FOUND",receipt:Receipt|null}`; use same advisory lock namespace with pg_try_advisory_xact_lock to distinguish in-flight work. No receipt/account leakage across users.
- Frontend API additions: depositsOptions(), prepareManualDeposit({amount,method,note},key), reviewTransfer(input), transactionReceipt(reference), operationStatus(key); send/completeDeposit optional AbortSignal. Existing interfaces remain compatible via optional receipt fields.

## Task1 — Backend (backend agent owns ALL Java, SQL, .env.example)
Write integration regressions first for manual variants/limits/notes/disabled/ownership/idempotency/rollback; quote identity/fees/changed review/duplicate/concurrency; authoritative receipt/history/recovery. Extend existing service/routes/DAO with additive V5, no competing money service. Keep old API/auth/GCash/QR/cards tests. Parent alone runs Maven to avoid overlapping compilers. No secret reads/output.

## Task2 — Shared receipts (receipt agent owns TransactionReceipt.tsx, receiptExport.ts, receipt CSS, TransactionList.tsx, BankingUi PaymentReceipt wrapper and its tests ONLY)
Original branded success receipt shared across deposit/transfer; mask all accounts; include fee/total only when backend supplied. PNG export via Canvas (no dependency), visible DEMO—NOT PROOF OF PAYMENT on every image. Native share on explicit click; download fallback; cancellation/errors handled. History reopens only receiptAvailable rows using api.transactionReceipt; no fabricated old receipts. Preserve cards and legacy title prop. Unit tests assert data/privacy/export and no success for pending/unknown.

## Task3 — Frontend forms/reliability (root owns API types/client, Payments, new ManualDeposit, useMoneyOperation, Dashboard, app integration and form tests)
Append separate Manual Deposit section below untouched GCash/QR cards. Config-driven amounts/method/note validation, disabled review, review/edit/confirm, receipt and another action. Upgrade existing transfer form to backend quote/review with masked account/fees/total/current balance; frozen quote for confirm, server revalidation; no pre-confirm debit. Persist pending confirm key+frozen payload in sessionStorage keyed by authenticated user (never tokens). Unknown timeout/lost-response must recover by key or replay identical payload+key; never edit/start another intent until resolved. Clear only successful or definitively failed operations. 30sec abort for confirmation only. Refresh live balance/history on confirmed success.

## Task4 — Review and verification (root + read-only reviewer)
Run frontend tests/build/lint, full Java opt-in integration package with disposable schemas, populated V4→V5 upgrade preservation. Add focused fixes/regressions, verify existing functionality. Document files/endpoints/migration/config/manual checks/limitations. No browser provider currently connected: rendered viewport/device checks must not be claimed.

## Ownership and interface review
Backend produces frozen DTOs consumed by root/receipt agent. Root owns api.ts exclusively; receipt agent imports its planned exports/types. Backend does not edit frontend. Receipt agent does not edit API/forms. Root does not edit Java while backend agent works. All prior uncommitted changes preserved. User requests direct workspace implementation; no git metadata writes/commits/merges.

## Progress
- Inspection/contracts complete. Implementation authorized by user workflow; proceed without redundant approvals.
- Task1 complete: new manual/review/receipt/recovery contracts implemented in existing services; additive V5, no reset. Initial9 focused tests green, then full81 green; note/method validation follow-ups and lock/rename regressions included in final83-test package.
- Task2 complete: shared masked signed receipts, actual canvas PNG export/native sharing, owned history retrieval;22 focused receipt tests green. Default outgoing title bug fixed with regression.
- Task3 complete: appended manual section and existing transfer review enhanced, persisted frozen attempts, 30sec timeout and same-key recovery. Rejected-deposit review-reset bug reproduced and fixed. Full frontend69 tests green, build passed, lint0 errors/8 warnings.
- Task4 complete: security/integration review and scoped fixes approved; all requested report/setup/manual checks/limitations in docs/manual-deposit-receipts.md. No new dependencies; .env preserved; no real SMS/payment executed by tests. Rendered browser/device checks unavailable.
