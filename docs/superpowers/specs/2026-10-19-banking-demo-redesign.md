# FuroCash banking demo extension

## Existing application / interpretation
Inspected: React 19 + TypeScript 6 + Vite 8 frontend, Java 21/Javalin 6.7 backend, prepared JDBC DAOs, PostgreSQL and Flyway V1?V3. No Angular, Spring Boot, payment provider, virtual card or QR feature exists. Preserve the actual stack and working PhilSMS/JWT/PIN flows rather than migrate or create a new project. User was asked about the stack discrepancy; current-stack implementation is the stated default.

Brand: keep FuroCash, its existing f. logo/favicon, green #145c48/#194a3a, lime #cbea9c, DM Sans and Manrope. Original mobile-first layout inspired by modern banking spacing/navigation, with no copied MariBank graphics.

## User experience
Shared desktop sidebar and mobile safe-area bottom navigation use one menu definition: Home, Transactions, Scan & Pay, Card, Me. Add Deposit and Transfer routes. Home has live greeting/balance/masked account and Deposit/Transfer/History/Scan actions. History reuses the existing endpoint/component and includes completed deposits, transfers and simulated card purchases.

Deposit: prepare a persisted GCASH_DEMO request with reference and amount, review, explicitly simulate completion, show a receipt and updated balance. Also generate/save a custom demo deposit QR. All QR views say DEMO ? NOT FOR REAL PAYMENTS; no GCash/QR Ph compatibility is claimed.

Transfer: look up recipient by FuroCash account identifier or existing mobile, display authoritative name, enter amount/note, review then confirm. QR generation persists a versioned request; scanning/upload parses only the FuroCash schema, resolves it with the backend, and opens the same transfer confirmation flow. Camera starts only on user action and stops on completion/unmount.

Cards: one internal card per user, identifiers start DEMO-FC (never a PAN/CVV/network logo), masked by default, owner name, demo expiration and ACTIVE/FROZEN status. Confirm create/freeze/unfreeze/limit changes. Daily limit and simulated purchases are enforced on the backend against the existing account balance. Purchase simulation provides a functional way to exercise spending limits/history. No Lithic credentials or external payment calls.

Profile: live full name, optional email, verified phone (read-only), derived FC account identifier and existing creation timestamp. Edit name/email. Reuse SMS PIN recovery for security; no password system is invented. Logout preserves current behavior.

## Persistence and security
Flyway V4 adds nullable users.email, completed transaction status/card relation, demo_deposits, qr_payment_requests, demo_cards and idempotency_operations. No table resets or data removal. Public account identifier is FC- plus user ID padded to at least ten digits (derived, not a secret). Existing users are supported without backfilling fictional data.

All new routes require existing JWT authentication. Identity is always AuthFilter.currentUserId. BigDecimal amounts: positive, maximum 999999999.00, at most two decimals. Balances and history change atomically under row locks. History is immutable. Demo deposit preparation/completion, transfers and simulated card purchases use an Idempotency-Key header; key reuse with changed payload returns 409. Existing endpoint paths and balance fields are retained; repository clients/tests are updated to send keys. Demo funding remains controlled by DEMO_CASH_IN.

QR schema: {app:"FUROCASH",version:1,purpose:"TRANSFER"|"DEPOSIT",accountIdentifier:string,reference:UUID,amount:string|null}. Requests expire after 24 hours. Fixed amounts cannot be modified. References, target and amount must match the persisted request. Transfer QR requests are consumed atomically once; scanning alone never moves money. No credentials in QR payloads.

## Shared API contracts
User adds accountIdentifier:string,email:string|null,createdAt:string|null. Existing fields remain.
Transaction adds status:"COMPLETED" and optional cardId; types add CARD_PURCHASE. Existing fields remain.
Receipt: {reference,kind,status,amount,balance,accountIdentifier,counterparty,note,createdAt}; amount/balance serialize as JSON numbers and are sent as decimal strings.
POST /api/deposits/demo {amount,method:"GCASH_DEMO"} -> Receipt(status PENDING); POST /api/deposits/demo/{reference}/complete {} -> Receipt(COMPLETED). Both require Idempotency-Key.
POST /api/transfers/validate-recipient {accountIdentifier?:string,receiverMobile?:string} -> {accountIdentifier,fullName,mobileNumber}.
POST /api/transfers {receiverAccount?:string,receiverMobile?:string,amount,note?:string,qrReference?:UUID} -> Receipt including existing balance. Requires Idempotency-Key.
POST /api/qr/requests {purpose,amount?:string|null} -> QrPayload; POST /api/qr/resolve {payload:QrPayload} -> {recipient:{accountIdentifier,fullName,mobileNumber},amount:string|null,reference,purpose}.
GET /api/users/me -> User; PATCH /api/users/me {fullName,email} -> User.
POST /api/cards {} -> Card; GET /api/cards/my -> Card|null; PATCH /api/cards/{id}/freeze, /unfreeze -> Card; PATCH /api/cards/{id}/limit {dailyLimit} -> Card; GET /api/cards/{id}/transactions -> Transaction[]; POST /api/cards/{id}/simulate-purchase {amount,merchant} -> Receipt (Idempotency-Key).
Card: {id,identifier,maskedIdentifier,cardholderName,status,dailyLimit,spentToday,expiresOn,createdAt}; balance remains the account balance, not stored on card.

## Verification
Keep existing tests, add isolated PostgreSQL HTTP tests for all money/idempotency/ownership/QR/card paths and React tests for navigation, forms and errors. QR codec tests include invalid schemas and generation/image decoding. Production builds, lint, formatting and responsive CSS checks at 320/375/430/768/1024/1440. Do not send real SMS or payment requests while testing. Browser/camera hardware limitations must be reported honestly.
