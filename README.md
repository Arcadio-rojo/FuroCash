# FuroCash

A connected local banking demo built with Java 21, Javalin, PostgreSQL, React, and TypeScript.

Includes mobile/PIN login, registration with OTP, forgot/reset PIN, session restoration, balance, transfers, paginated transaction history, and optional simulated cash-in. PIN resets invalidate all previous sessions. Codes expire in five minutes, allow three attempts, and have a 60-second resend cooldown.

## Run on Windows

Requirements: Java 21+, Node.js 24 LTS, Maven 3.9+ (or IntelliJ with bundled Maven), and PostgreSQL. Docker Desktop is optional.

From this project folder, with your existing PostgreSQL database:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run.ps1 -DemoCashIn
```

The script builds both projects and starts the complete app at **http://localhost:7070**. It finds IntelliJ’s bundled Maven if Maven is missing from PATH. Configure your PhilSMS API token and an enabled Sender ID in `.env` first. Verification codes are sent to your phone by SMS. If another backend already occupies port 7070, stop that instance or add `-Port 7071`.

For a **new Docker database**, use:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run.ps1 -StartDatabase -DemoCashIn
```

If `.env` is missing, the script creates it with random database credentials and a random JWT secret. You still need to add your PhilSMS account credentials before the backend can send SMS. If `.env` already exists, it is preserved. For an existing PostgreSQL installation, make sure `.env` matches its credentials. The example defaults to port 5433; an existing database on port 5432 needs `DB_URL=jdbc:postgresql://localhost:5432/furocash`.

After the first build, `-SkipBuild` starts the existing jar immediately. Java serves the built frontend directly, so a separate Vite process is unnecessary.

## Manual setup / frontend development

1. Copy `.env.example` to `.env`. Set your database password, a **random** JWT secret of at least 32 characters, and your PhilSMS API token and Sender ID. Placeholder secrets are rejected.
2. Start PostgreSQL (`docker compose up -d --wait db` if using Docker).
3. Build and run from the project root:

```text
cd frontend
npm ci
npm run build
cd ..
mvn package
java -jar target/furocash.jar
```

Flyway applies migrations automatically. Keep existing migrations unchanged. V3 adds purpose-specific codes and session versions; V4 adds demo cards, deposit requests, QR requests, idempotency records, profile email, and immutable transaction history. Existing users and balances remain in place.

For hot reload, keep the Java backend running and run `npm run dev` inside `frontend/`, then open http://localhost:5173. Vite forwards `/api` requests to port 7070. If your backend uses another port, update the proxy target in `frontend/vite.config.ts`. For separate hosting, copy `frontend/.env.example` to `frontend/.env` and set `VITE_API_BASE_URL` to your backend origin; set backend `FRONTEND_ORIGIN` to your frontend origin.

Only public settings belong in `VITE_` variables. Database, JWT, and SMS secrets stay in the root `.env`, which Git ignores. Explicit environment variables override `.env`; JVM `-D` properties override both.

## Try the flows

The banking redesign keeps this project's existing React/Javalin architecture. See [the banking feature guide](docs/banking-demo.md) for the new pages, API contracts, and demo payment boundaries.

- **Register:** enter a name, an 11-digit `09` mobile number, and a four-digit PIN twice. Enter the six-digit registration code received by SMS on that mobile number. You enter the dashboard automatically.
- **Login:** enter your registered mobile and PIN. Refreshing the browser restores the session. Three failed PIN attempts lock the account for five minutes.
- **Dashboard:** enable `DEMO_CASH_IN=true` (or use `-DemoCashIn`) to add simulated funds. Register a second test account to try transfers, then inspect each account’s transaction history.
- **Forgot PIN:** request a recovery code, receive it by SMS, and submit it with your new PIN. Log in again with the new PIN; previous sessions are rejected.
- **Logout:** clears the token from this browser. Bearer tokens expire after one hour. Treat test tokens like credentials.

## Real SMS setup (registration and forgot PIN)

Both flows use PhilSMS real SMS by default. In the **project-root** `.env`, configure:

```env
SMS_MODE=philsms
PHILSMS_API_BASE_URL=https://dashboard.philsms.com/api/v3/
PHILSMS_API_TOKEN=your_actual_api_token
PHILSMS_SENDER_ID=your_enabled_sender_id
```

Create/sign in to your [PhilSMS dashboard](https://dashboard.philsms.com/), add SMS credits, and use a Sender ID enabled in your account. The example `PhilSMS` must be permitted by your account; replace it with your approved custom Sender ID if needed. The backend calls `POST /api/v3/sms/send` with Bearer authentication and a JSON payload, normalizing local `09...` mobile numbers to `639...`. See the [official API documentation](https://app.philsms.com/developers/documentation).

Keep the API token in the backend `.env`; never paste it into frontend code or a `VITE_` variable. Restart the Java backend after changing configuration. A Vite-only restart does not reload backend settings. If you use `-SkipBuild`, rebuild once after code changes before using that flag again.

Registration: **Continue ? SMS code screen ? verify ? account created**. The account is not created until the OTP is verified. PIN recovery requires the SMS recovery code before changing the PIN. Resend remains subject to the 60-second cooldown.

The backend fails startup if real SMS is selected without an API token; it never silently falls back to terminal codes. Provider rejection returns an error and the form stays on its current step. Accepted messages may still take time to arrive because the mobile network handles delivery. No real SMS is sent by automated tests.

For explicit offline development/testing only, `SMS_MODE=console` remains available. That mode does not send real SMS and does not match the phone-delivery flow described above.

## Checks

```text
mvn test
cd frontend
npm test
npm run build
npm run lint
```

Real database integration tests are opt-in:

```text
mvn -Dintegration=true test
```

These connect using `.env`, create a unique temporary schema, apply migrations, test complete HTTP flows, and remove that schema afterward. The database user needs CREATE SCHEMA permission. Optionally provide `TEST_DB_URL` to point at a separate test database. Existing application tables are not touched.

## Project layout

- `frontend/src/screens`: login, registration, OTP verification, recovery/reset, dashboard.
- `frontend/src/context`: session restoration and login/logout state.
- `frontend/src/components`: shared forms, auth layout, balance and history.
- `frontend/src/api.ts`: frontend API contract, bearer tokens, error handling.
- `src/main/java/com/furocash/web`: HTTP routes and authentication filter.
- `src/main/java/com/furocash/service`: authentication, OTP, balances and transfers.
- `src/main/java/com/furocash/dao`: prepared SQL and persistence.
- `src/main/resources/db/migration`: versioned database migrations.

This project uses simulated funds. Cash-in does not connect to a payment processor. A production financial application needs additional payment verification, stronger authentication, operational controls, and independent security review.
