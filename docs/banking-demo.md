# Banking demo guide

FuroCash uses its existing React 19 / TypeScript / Vite frontend and Java 21 / Javalin / PostgreSQL backend. The codebase does not contain Angular or Spring Boot. The redesign preserves the current application and its green/lime branding, login, SMS OTP, PIN recovery, JWT sessions, and user records.

## Run

From the repository root in PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run.ps1 -DemoCashIn
```

Open http://localhost:7070. Stop an older backend instance first, or pass `-Port 7071`. Rebuild after these source changes; do not use `-SkipBuild` until the new build has completed. For frontend hot reload, run `npm run dev` in `frontend` while the backend runs on port 7070.

Keep existing root `.env` credentials. `DEMO_CASH_IN=true` enables simulated deposits; `-DemoCashIn` enables it for the startup script. SMS settings remain on the backend. Never place SMS, database, or JWT secrets in frontend environment variables.

## Demo flows

- **Home:** authenticated name, PHP account balance, visibility control, and quick actions.
- **Deposit:** prepare a demo GCash request, review its reference, and explicitly simulate completion. This credits internal demo funds only. Deposit QR codes are custom FuroCash requests and cannot be presented as GCash or QR Ph compatible payment codes.
- **Transfer:** look up a registered FuroCash account, review the recipient and amount, then confirm. A second account is needed to test receiving funds. Existing mobile-number transfers remain supported by the API.
- **Scan & Pay:** generate a request, activate the camera, upload an image, or enter account details manually. Only persisted FuroCash version 1 QR requests are accepted. Requests expire after 24 hours; transfer requests can be paid once. Scanning never submits a transfer automatically.
- **Card:** create one internal demo card, freeze/unfreeze it, set its daily limit, and simulate purchases. The card shares the account balance. Its `DEMO-FC` identifier has no real payment network use. No PAN, CVV, Visa/Mastercard credentials, or Lithic account is required.
- **Me:** view account details, edit name/email, use the existing SMS PIN recovery flow, and sign out. Verified phone numbers are read-only.
- **Transactions:** deposits, transfers, and simulated card purchases use the same history. Completed history is immutable.

## Theme and loading

Use the sun/moon button on login screens or the dashboard header to switch light/dark mode. The preference is saved as `furocash_theme`; the first visit follows the device preference. It applies to forms, navigation, cards, receipts, and dialogs. On small screens the control shows its icon and keeps its accessible label.

The GCash demo now offers manual amount entry and PHP 100/500/1,000/5,000 presets, a review step, an internal reference, and an explicit completion action. No GCash credentials or external payment reference is needed. `DEMO_CASH_IN=true` is enabled in the current local `.env`; restart the backend to load it. Other environment settings are preserved.

Loading uses actual pending API requests, including response body reads: a top progress bar, balance/history/form skeletons, and operation spinners. After eight seconds a connection message asks you to wait. Indicators finish on response, failure, or cancellation; there is no artificial payment-success timer. Reduced-motion preferences disable animated effects. No new dependencies or database changes are needed for this update.

## API

All banking endpoints require the existing Bearer token. The server obtains account ownership from the verified token; requests do not select a user ID.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/account/balance` | Existing account balance |
| POST | `/api/account/cash-in` | Existing simulated funding route |
| GET | `/api/transactions` | Existing paginated history |
| POST | `/api/deposits/demo` | Prepare `{amount, method:"GCASH_DEMO"}` |
| POST | `/api/deposits/demo/{reference}/complete` | Explicitly complete demo deposit |
| POST | `/api/transfers/validate-recipient` | Resolve account identifier or mobile |
| POST | `/api/transfers` | Existing route, with account/note/QR support |
| POST | `/api/qr/requests` | Persist a TRANSFER or DEPOSIT request |
| POST | `/api/qr/resolve` | Validate payload against saved request |
| GET / PATCH | `/api/users/me` | View/update name and email |
| POST | `/api/cards` | Create internal demo card |
| GET | `/api/cards/my` | Current card or `null` |
| PATCH | `/api/cards/{id}/freeze` | Freeze owned card |
| PATCH | `/api/cards/{id}/unfreeze` | Unfreeze owned card |
| PATCH | `/api/cards/{id}/limit` | Set `{dailyLimit}` |
| GET | `/api/cards/{id}/transactions` | Owned card history |
| POST | `/api/cards/{id}/simulate-purchase` | Internal `{amount,merchant}` purchase |

Deposit preparation/completion, cash-in, transfers, and simulated purchases require an `Idempotency-Key` header (8–80 letters, digits, dots, underscores, colons, or hyphens; first character must be alphanumeric). Reuse the same key when retrying the same request. Reusing a key with different details returns HTTP 409. Repository clients have been updated; older API clients must add this header.

Amounts are positive decimal values with at most two fractional digits, maximum PHP 999,999,999.00 per request. Backend money operations use `BigDecimal`, database transactions, and row locks. QR transfers validate the saved recipient, amount, expiry, and one-time use inside the money transaction.

## Database and dependencies

Flyway `V4__banking_demo_features.sql` adds `demo_cards`, `demo_deposits`, `qr_payment_requests`, and `idempotency_operations`; it extends users with nullable email and transactions with status/card linkage. It does not reset tables or remove user data. Standard startup applies it automatically. Transaction UPDATE/DELETE is rejected by a database trigger.

Frontend QR dependencies: `qrcode` for generation, `qr-scanner` for camera/image scanning, and development types `@types/qrcode`. No external payment SDK or new backend dependency is required for these features.

## Changed code

The banking work adds or extends these groups within the existing project:

| Area | Main files |
| --- | --- |
| App shell and dashboard | `frontend/src/App.tsx`, `frontend/src/screens/Dashboard.tsx`, `frontend/src/index.css` |
| Demo money forms | `frontend/src/screens/Payments.tsx`, `frontend/src/screens/useOperation.ts` |
| QR | `frontend/src/qr.ts`, `frontend/src/screens/ScanPay.tsx` |
| Card and profile | `frontend/src/screens/CardPage.tsx`, `frontend/src/screens/ProfilePage.tsx` |
| Shared UI and API | `frontend/src/components/BankingUi.tsx`, `ConfirmDialog.tsx`, `BalanceCard.tsx`, `TransactionList.tsx`, `frontend/src/api.ts` |
| Theme/loading update | `frontend/src/theme.ts`, `appearance.css`, `components/ThemeToggle.tsx`, `LoadingState.tsx`, `NetworkActivity.tsx`; `tests/loading.test.tsx` and API/banking regressions |
| Wallet backend | `DemoWalletService.java`, `IdempotencyService.java`, `DemoRoutes.java`, `BankingSupport.java` |
| Card/profile backend | `CardService.java`, `ProfileService.java`, `CardRoutes.java`, `ProfileRoutes.java` |
| Integration | `Main.java`, `AuthFilter.java`, `AccountRoutes.java`, `AccountService.java`, user/transaction DAOs, models, and DTOs |
| Persistence | `src/main/resources/db/migration/V4__banking_demo_features.sql` |
| Java tests | `DemoIntegrationTestSupport.java`, `WalletDemoIntegrationTest.java`, `CardProfileIntegrationTest.java`, `MigrationUpgradeIntegrationTest.java`; existing banking HTTP tests now send idempotency headers |
| Frontend tests/tooling | `frontend/tests/api.test.mjs`, `qr-image.test.mjs`, `flows.test.tsx`, `banking.test.tsx`, `qr.test.tsx`, package manifests, test configuration |
| Startup/docs | Existing `scripts/run.ps1` is reused; root README and this guide describe setup |

Backend files above are under `src/main/java/com/furocash`, and Java tests under `src/test/java/com/furocash/web`. No framework migration or new application project was created.

## Checks

```text
cd frontend
npm test
npm run build
npm run lint
cd ..
mvn -Dintegration=true package
```

Integration tests create and remove unique PostgreSQL schemas, use a fake SMS sender, and never send actual SMS or external payments. They require database credentials and CREATE SCHEMA permission. Set `TEST_DB_URL` to use a separate test database if desired.

Camera access requires a supported browser and localhost or HTTPS. Mobile camera permission and actual-device layout checks must be exercised on the target devices. Test/build results and any unverified areas are recorded separately at delivery.

### Verified on 2026-10-20

- Java: `mvn -Dintegration=true package` passed all 70 tests, with zero failures, errors, or skips. This includes existing authentication/SMS adapter regressions, wallet/card ownership and concurrency, strict QR schema validation, static frontend hosting, and a populated V3-to-V4 upgrade that preserves user/balance/history data.
- Frontend: 13 Node tests and 27 React/QR/loading tests passed after the appearance update (40 total). This includes saved theme preference, manual deposit presets/custom input, progress through concurrent response bodies, failure/cancellation cleanup, and delayed connection guidance. QR generation was checked against the installed scanner's actual pixel decoder for both transfer and deposit payloads; a blank image returns no result.
- Production frontend build passed. Lint reports zero errors and five warnings (mixed component/helper exports and async loading effects).
- After the final frontend rebuild, both static hosting tests passed again: the Java server serves the new HTML/assets and returns 404 for the root `.env` file.
- After the theme/loading update, three focused backend checks passed again: latest HTML/assets hosting, private environment-file exclusion, and confirmed/idempotent demo deposit completion.
- Final integration review findings were fixed and re-reviewed: abandoned QR context, stale profile saves, skip-link routing, card dialog keyboard controls, and UUID API types.
- Live browser and camera verification was unavailable: the connected browser inventory was empty. Layout rules cover mobile/tablet/desktop, but rendered checks at 320/375/430/768/1024/1440 pixels and physical-device camera permission behavior remain unverified.

Automated tests use fake SMS and internal demo funds. They do not prove actual PhilSMS delivery, real GCash/QR Ph compatibility, real settlement, or real card issuing; these external payment integrations are not part of this demo.

QR library references: [qr-scanner documentation](https://github.com/nimiq/qr-scanner) and [qrcode documentation](https://github.com/soldair/node-qrcode).
