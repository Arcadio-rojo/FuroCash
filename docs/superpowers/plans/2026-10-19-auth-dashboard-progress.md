# Execution ledger: 2026-10-19-auth-dashboard

- User requested implementation of the prior proposed design. Executing inline with writing-plans, test-driven-development and verification-before-completion skills.
- Working in supplied workspace because managed .git is read-only; no branch/worktree/commit mutations.
- Shared interfaces: backend LoginResponse and UserResponse feed frontend session context; reset accepts mobile/otp/pin; register accepts mobile/fullName/pin/otp and returns a session.
- Local SMS defaults to console; cash-in is an explicitly enabled demo feature.

- Task 1 complete: 32 Java tests passed, including seven real PostgreSQL HTTP integration scenarios and two static-hosting tests. Every schema used by the integration suite was isolated and dropped afterward.
- Task 2 complete: ten API boundary tests and eight React flow tests passed; production build and lint passed. Frontend tests cover StrictMode restoration, retryable outages, login/logout, registration OTP, PIN recovery, demo funding, and indeterminate funding responses.
- Task 3 complete: runnable shaded jar built; startup script syntax verified and launched with -SkipBuild; packaged frontend, JS/CSS and health returned HTTP 200. README and environment examples provided.
- Independent final review found two important API issues. Fixed both with red-to-green regressions: response-read failures/malformed successful responses now carry outcomeUnknown, and old-session 401s cannot clear a newer login token.
- Browser providers were unavailable, so visual browser QA could not run. React DOM flow tests and HTTP static-hosting checks passed.
- Temporary approval-review quota failure blocked two commands initially. After the reported reset time passed, approval succeeded and packaging/development-tool installs completed.
- The restricted Java filesystem sandbox rejected static-file aliases. The focused hosting tests and actual startup script succeeded outside that sandbox, without weakening alias checks.

## Real SMS follow-up
- User requested phone SMS OTP for registration and PIN recovery instead of console delivery.
- Traced cause to SMS_MODE=console and absent Semaphore credentials; changed root .env and example to semaphore without printing or replacing credentials.
- Provider response regressions reproduced HTTP-200 failed/malformed-message acceptance and silent fallback on an invalid SMS mode. Added local HTTP tests and strict message/status validation; no live SMS sent.
- Registration and PIN recovery now direct users to their SMS inbox, retain verification and resend steps, and remain on the original form when provider submission fails.
- External dependency remains: user must configure a Semaphore API key, credits and active/default sender name. No API key was present during implementation; actual device delivery cannot be verified without that configuration.

- Follow-up verification: 45 Java tests passed (including local SMS provider fixtures and nine isolated PostgreSQL scenarios); 20 frontend tests passed; lint and production build passed; runnable jar rebuilt. Root SMS_MODE is semaphore; API key still absent, so live SMS delivery is pending user configuration.

## PhilSMS provider switch
- User selected PhilSMS and supplied its dashboard API endpoint/token. Saved the token only in ignored root .env; never echoed it or added it to source/tests/examples.
- Added PhilSmsSender with Bearer authentication, JSON payload, normalized 639 recipient numbers, explicit sender ID, HTTPS endpoint validation, no credential forwarding through redirects, and sanitized error handling.
- Selected SMS_MODE=philsms, PHILSMS_API_BASE_URL=https://dashboard.philsms.com/api/v3/, and PHILSMS_SENDER_ID=PhilSMS (configurable; user asked to confirm the enabled sender).
- Read-only GET /api/v3/balance returned HTTP 200 and status success using the provided token. No live SMS was sent and no message credits were used by tests.
- Eight PhilSMS HTTP contract tests and six provider-selection tests passed. Full verification pending below.

- PhilSMS final verification: 55 Java tests and 20 frontend tests passed; lint, production build, credential scan and runnable jar packaging passed. Token authentication succeeded via a read-only provider request; device delivery was not tested because no recipient was supplied.
