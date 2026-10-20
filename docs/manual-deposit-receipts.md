# Manual deposits and transfer receipts — delivery

Implemented in the existing React/TypeScript/Vite and Java21/Javalin/JDBC project. No framework or payment integration was added. Existing GCash Demo, Deposit QR, Scan & Pay, card controls, authentication, navigation, theme, and loading features remain.

## Features

The separate **Manual Deposit** section appears below the original deposit cards. It supports four demo funding labels, PHP0.01 to the server-configured limit (defaultPHP50,000), and optional notes up to150 characters. Review creates only a pending request; **Confirm Demo Deposit** credits the wallet atomically. A completed backend receipt supplies its method, amount, fee, reference, timestamp, account and balance.

The existing transfer form now requests an authoritative five-minute review before displaying **Confirm Transfer**. It shows the backend-verified recipient, masked account, amount, fee, total deduction and available balance. Confirmation revalidates the reviewed details and available funds under row locks. A changed/expired/consumed review cannot pay. The original endpoint remains compatible with existing non-review API clients.

Both flows use a shared receipt with signed amounts, account masking, light/dark styling, completion announcement, PNG download and native sharing. Every exported image and shared text includes **DEMO — NOT PROOF OF PAYMENT**. Unsupported sharing falls back to a download; user cancellation is handled. No export contains PINs, OTPs or authentication tokens.

History exposes **View receipt** only for supported completed transactions. Receipts are stored in the same database transaction as balances/history, scoped to their owner and immutable. Older unsupported transactions are preserved and do not get fabricated receipts. Incoming transfers get the receiver's own receipt and balance snapshot.

## Network safety

Before confirming, the frontend saves the frozen request, original idempotency key and review in `sessionStorage`, scoped to the authenticated user. No token is stored in that journal. A confirmation aborts client waiting after30 seconds, then checks the original operation. If the outcome remains uncertain, editing/new intent is blocked: only status checking or resending the exact request/key is available. A remounted page recovers the saved attempt. Duplicate requests return the original result.

Recovery storage lasts for the current browser tab. If the tab is closed and its storage is discarded, inspect transaction history before initiating another intentional transaction. There is no cross-device pending-intent synchronization. Backend idempotency and account locks still enforce every submitted key and prevent overspending.

## API

All listed endpoints require the existing Bearer session. Account ownership comes from that verified session.

| Method | Endpoint | Use |
| --- | --- | --- |
| GET | `/api/deposits/options` | Enabled flag, limits, methods, fee rule |
| POST | `/api/deposits/demo` | Reused: prepare GCash or manual method with amount/note |
| POST | `/api/deposits/demo/{reference}/complete` | Reused: confirm deposit with Idempotency-Key |
| POST | `/api/transfers/review` | Create authoritative reviewed snapshot |
| POST | `/api/transfers` | Reused: confirm with optional reviewReference and Idempotency-Key |
| GET | `/api/operations/{key}` | Owner-scoped COMPLETED / PROCESSING / NOT_FOUND recovery |
| GET | `/api/transactions/{reference}/receipt` | Retrieve immutable owner receipt,404 if unavailable |
| GET | `/api/transactions` | Reused history with receiptAvailable flag |

## Migration/configuration

Flyway `V5__manual_deposits_and_receipts.sql` extends permitted deposit methods and adds the note column, `transfer_reviews`, and `transaction_receipts`. It does not drop application tables, reset balances, rewrite history, or backfill invented receipts. Startup applies it automatically. V1–V4 remain unchanged.

```env
DEMO_CASH_IN=true
DEMO_MANUAL_DEPOSIT_MAX=50000
```

Demo funding defaults to disabled unless explicitly enabled. `.env.example` keeps it false. The existing local `.env` was preserved in this task. Restart Java after changing configuration. No new dependencies or credentials are needed for receipt export or manual funding.

Start the newly built application from the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run.ps1 -DemoCashIn -SkipBuild
```

Open http://localhost:7070. Stop an older instance first if it occupies that port. For hot reload use the existing Vite development workflow.

## Files

- Backend: `DemoWalletService.java`, `IdempotencyService.java`, `TransactionDao.java`, `Receipt.java`, `TransferRequest.java`, `TransactionResponse.java`, `Transaction.java`, `DemoRoutes.java`, `AccountRoutes.java`, `AuthFilter.java`.
- Database/config: V5 migration and `.env.example`.
- Forms/recovery: `frontend/src/screens/ManualDeposit.tsx`, `Transfer.tsx`, `useMoneyOperation.tsx`, `moneyFlows.css`; existing Payments/Dashboard/API wiring.
- Receipts/history: `frontend/src/components/TransactionReceipt.tsx`, its CSS, `TransactionList.tsx`, existing PaymentReceipt wrapper, and `frontend/src/receiptExport.ts`.
- Tests: `ManualReceiptIntegrationTest.java`, `ManualMigrationIntegrationTest.java`, updated upgrade regression, `manual-transfer.test.tsx`, `receipts.test.tsx`, `receipts-export.test.tsx`, and existing banking/auth-flow regression fixtures.

Backend sources are under `src/main/java/com/furocash`; Java HTTP tests under `src/test/java/com/furocash/web`.

## Executed verification

- `mvn -Dintegration=true package`: **83 passed**, zero failures/errors/skips. Includes limits, methods, disabled funding, invalid/control-character input, ownership, review expiry/rename, concurrent review confirmation, idempotency, immutable receipts, operation-status locking, injected receipt-write rollback, and populated V4→V5 preservation. Existing auth/QR/card/wallet tests also passed.
- `npm test`: **69 passed** (13Node +56React/receipt/QR/loading), including double clicks, timeout/same-key retry, lost reply recovery after remount, review/edit/re-confirmation, backend-provided fee display, PNG canvas output/watermark/masking, native share/fallback/cancellation and history receipt access.
- Frontend production build passed. Lint completed with zero errors and eight warnings (mixed helper/component exports, async loading effects, intentional note-control regex validation).
- Code review findings were fixed and scoped re-review approved. Final logs: `.cache/manual-final.log`, `frontend/manual-all-tests.log`, `manual-build.log`, and `manual-lint.log`.

## Manual acceptance checks

1. Log in and open Deposit. Confirm that both original cards remain, then scroll to Manual Deposit.
2. Enter1000, select Bank Transfer (Demo), add a note, and choose Review Deposit. Balance/history must remain unchanged. Back to Edit must preserve entered fields.
3. Confirm Demo Deposit. Check the completed reference, +PHP1,000 credit and refreshed wallet/history. Download the PNG and inspect masking and demo watermark. Use Make Another Deposit to start a fresh intent.
4. With a second existing FuroCash account, enter its identifier and500 in Transfer. Review the backend name, masked account and total. Confirm, verify sender/receiver balances and both history rows, then reopen/download/share the receipt.
5. Test light/dark mode and narrow screens; use only the keyboard to complete forms. Disable demo funding and restart Java: manual confirmation must be unavailable while existing methods remain visible.

Live rendered viewport checks, physical-device camera behavior, and a real OS native share sheet were unavailable in the connected environment. Browser APIs are exercised through tests with native canvas/share mocks; these do not replace those device checks. All money and payment methods remain internal simulations.
