# Connected Auth and Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Deliver the approved connected local banking demo.
**Architecture:** Extend existing DAOs/services/routes with purpose-specific transactional OTPs and versioned JWT sessions. Separate frontend screens using shared context and forms; preserve the current stack.
**Tech Stack:** Java 21, Javalin 6.7, PostgreSQL, Flyway, React 19, TypeScript 6, Vite 8.
**Spec:** docs/superpowers/specs/2026-10-19-auth-dashboard-design.md

## Global Constraints
- Never store PINs or OTPs in browser storage.
- Five-minute OTP expiry, three attempts, sixty-second resend cooldown.
- Demo cash-in requires explicit configuration.
- Work in the supplied workspace; managed .git metadata is read-only. Leave changes uncommitted.

## Review Focus
- Wrong OTP counters must survive transaction rollback.
- Concurrent OTP use and login must not bypass attempt limits.
- A previous token must fail after PIN reset.
- Refresh and temporary API failure must not trap or silently log out the user.
- Repeated clicks must not create overlapping money requests; transport failure must not invite automatic retries.

### Task 1: Backend auth, recovery and sessions
**Files:** configuration, UserDao/AuthService/OtpDao/OtpService/JwtUtil/AuthFilter/AuthRoutes/ApiExceptionHandler; new migration, reset DTOs and PostgreSQL integration tests.
**Interfaces:** POST /api/auth/register/request-code, POST /api/auth/register -> LoginResponse; POST /api/auth/login -> LoginResponse; GET /api/auth/me -> UserResponse; POST /api/auth/forgot-pin {mobile}; POST /api/auth/reset-pin {mobile,otp,pin}.
- [x] Add tests for register OTP, invalid/reused/purpose-mismatched codes, reset/revocation, lockout and API errors; run red.
- [x] Implement transactional OTP/session/version behavior; run Java suite green.

### Task 2: Frontend flows and dashboard
**Files:** frontend/src/api.ts, App.tsx, context/AuthContext.tsx, components, screens, index.css, API tests.
**Interfaces:** backend contracts above plus existing account/transfer/history routes; all currency inputs sent as decimal strings.
- [x] Add API boundary tests for OTP payloads, recovery, session failures and non-JSON errors; run red.
- [x] Implement context, navigation, shared fields, screens and responsive dashboard.
- [x] Run frontend tests, build and lint.

### Task 3: Run configuration and whole-app verification
**Files:** pom.xml, .env.example, frontend/.env.example, vite.config.ts, README.md, local start scripts and .gitignore.
- [x] Provide runnable jar and local startup that loads environment without exposing secrets.
- [x] Verify isolated database integration and browser journey when infrastructure permits.
- [x] Review final changes, rerun checks, and document exact startup commands and remaining infrastructure requirements.
