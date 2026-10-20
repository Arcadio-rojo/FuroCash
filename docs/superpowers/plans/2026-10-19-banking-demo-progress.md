# Execution ledger ? plan: docs/superpowers/plans/2026-10-19-banking-demo-redesign.md

- Inspection complete: React 19/Javalin 6.7, not Angular/Spring. Working auth/PhilSMS/user balance/transfers/history preserved. No real payment/card/QR integration exists.
- Ruling: preserve actual stack after disclosing mismatch and asking an optional clarification ? matches no-rewrite/preserve requirement; cost if wrong: framework migration would require separate work.
- User explicitly requested implementation plan followed by execution; no repeated authorization gate.
- Subagent-driven-development and dispatching-parallel-agents skills apply to the independent frontend and card/profile domains after shared contracts are fixed. Root owns wallet/migration/integration.
- Preflight shared interface: Task1 Receipt + IdempotencyService -> Task3 card purchase; signature and response fields frozen in spec.
- Preflight shared interface: Tasks1/3 API DTOs -> Task2 frontend; account identifier, QR, card, receipt names frozen in spec.
- Preflight shared files: root owns Main/AuthFilter/migration/AccountRoutes; backend agent owns User/UserDao/UserResponse/Card/Profile files; frontend agent owns frontend. No overlapping edits intended.
- Ruling: money endpoints require Idempotency-Key; preserve paths and balance response field, update repository consumers/tests. This necessary security extension may require older external clients to add the header.
- Tests use per-class disposable PostgreSQL schemas and separate Surefire forks because the existing pool is static.
- Continuation recovery: earlier agents were no longer active; resumed the incomplete frontend task and scoped backend reviews using fresh agents, without restarting completed wallet implementation.
- Wallet backend implemented: confirmed deposits, account/mobile transfer lookup, authoritative QR requests, immutable history, transactional row locks, and persisted idempotency receipts. Initial 6 integration tests passed.
- Card/Profile routes mounted in Main. Initial 4 card/profile integration tests passed; review fixed control-character email validation and added retry/ownership regressions.
- Wallet review: no critical/important findings. Added concurrent distinct-key deposit completion and QR payment tests plus opposite-direction transfer regression to close reported coverage gaps.
- Expanded banking suite: 14 tests passed (9 wallet, 5 card/profile), 0 failures/errors; .cache/banking-expanded.log.
- Added a populated V3-to-V4 upgrade regression; full Java package/regression run pending.
- Frontend task remains in progress: incomplete Scan & Pay/Card/Me and responsive styling are being finished. Do not claim completion yet.
- Browser getState timed out; actual camera/device layout checks remain unverified unless later browser tooling succeeds.
