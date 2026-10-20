# Connected authentication and dashboard

The user approved the proposed screen/package structure and requested a fully connected local app. Keep Java 21/Javalin/PostgreSQL and React/TypeScript/Vite. Implement login, registration with OTP, forgot/reset PIN, authenticated session restoration, dashboard balances, paginated history, transfer and explicit demo cash-in. Follow the current repository structure; do not publish or commit user work.

Registration and PIN reset codes have separate purposes, expire after five minutes, allow three attempts and require sixty seconds between sends. Verify and consume a code in the same database transaction as account creation or PIN change. Preserve failed-attempt updates when verification fails. Serialize login and OTP operations. PIN changes increment a session version checked for every authenticated request. Return generic recovery-request responses for unknown users. Console SMS is the local default; Semaphore remains optional.

The frontend uses separate screens and shared form/layout components, a session context, hash navigation (no extra router dependency), mobile validation, confirmation PINs, resend timers, loading/errors and authenticated routes. Persist only the bearer token, never PINs/OTPs. Restore sessions with GET /api/auth/me, handle 401 globally, and leave transient backend errors retryable. Use a same-origin /api proxy in development and serve the built frontend from the Java server for a single-process production preview.

Configuration loads .env locally without replacing explicit environment variables. Add a runnable shaded jar, local start scripts, environment examples, and setup documentation. Demo cash-in must be explicitly enabled by configuration. This is a local banking demo, not a payment processor.

Verification: Java utility and PostgreSQL integration tests for purpose separation, wrong/expired OTPs, one-time consumption, reset session revocation, lockouts, protected routes and money/history; frontend API tests and build/lint; browser flow where available. Test database schemas must be isolated and cleaned up. Report unavailable infrastructure honestly.
