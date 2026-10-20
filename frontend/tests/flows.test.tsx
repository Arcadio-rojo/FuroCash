import { StrictMode } from "react";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, cleanup } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "../src/App";
const user = {
  id: 1,
  mobileNumber: "09171234567",
  fullName: "Demo User",
  balance: 100,
};
let calls: { path: string; body: Record<string, string> | null }[];
let balance: number;
let history: unknown[];
let meStatus: number;
let smsError: boolean;
function reply(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
beforeEach(() => {
  localStorage.clear();
  history = [];
  balance = 100;
  meStatus = 200;
  smsError = false;
  calls = [];
  window.history.replaceState(null, "", "#/login");
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: string, options: RequestInit = {}) => {
      const path = new URL(input, "http://localhost").pathname;
      const body = options.body ? JSON.parse(String(options.body)) : null;
      calls.push({ path, body });
      if (path === "/api/auth/me")
        return reply(
          meStatus === 200 ? user : { error: "Temporarily unavailable" },
          meStatus,
        );
      if (path === "/api/auth/login")
        return reply({ token: "valid-token", user });
      if (path === "/api/auth/register")
        return reply(
          { token: "new-token", user: { ...user, fullName: "New User" } },
          201,
        );
      if (
        [
          "/api/auth/register/request-code",
          "/api/auth/forgot-pin",
          "/api/auth/reset-pin",
        ].includes(path)
      )
        return smsError
          ? reply(
              { error: "The SMS provider did not accept the message." },
              502,
            )
          : reply({ message: "OK" });
      if (path === "/api/account/balance") return reply({ balance });
      if (path === "/api/transactions") return reply(history);
      if (path === "/api/health")
        return reply({ status: "UP", demoCashIn: true, smsMode: "console" });
      if (path === "/api/deposits/options")
        return reply({
          enabled: true,
          minAmount: 0.01,
          manualMaxAmount: 50000,
          fee: 0,
          methods: [{ value: "CASH_DEMO", label: "Cash Deposit (Demo)" }],
        });
      if (path === "/api/deposits/demo")
        return reply({
          reference: "12345678-1234-4234-8234-123456789abc",
          kind: "DEPOSIT",
          status: "PENDING",
          amount: Number(body.amount),
          balance,
          accountIdentifier: "FC-0000000001",
          counterparty: "GCASH_DEMO",
          note: null,
          createdAt: "2026-10-19T10:00:00",
        });
      if (path.endsWith("/complete")) {
        balance += 25.5;
        history = [
          {
            id: 1,
            type: "CASH_IN",
            amount: 25.5,
            details: "Cash in",
            referenceNo: "FC-TEST",
            balanceAfter: balance,
            createdAt: "2026-10-19T10:00:00",
          },
        ];
        return reply({
          reference: "12345678-1234-4234-8234-123456789abc",
          kind: "DEPOSIT",
          status: "COMPLETED",
          amount: 25.5,
          balance,
          accountIdentifier: "FC-0000000001",
          counterparty: "GCASH_DEMO",
          note: null,
          createdAt: "2026-10-19T10:00:00",
        });
      }
      throw new Error("Unexpected request " + path);
    }),
  );
});
afterEach(() => {
  cleanup();
  localStorage.clear();
  window.history.replaceState(null, "", "/");
  vi.unstubAllGlobals();
});
async function login() {
  const actor = userEvent.setup();
  await actor.type(screen.getByLabelText("Mobile number"), "09171234567");
  await actor.type(screen.getByLabelText("4-digit PIN"), "1234");
  await actor.click(screen.getByRole("button", { name: /Log in to FuroCash/ }));
  await screen.findByRole("heading", { name: /Hello, Demo/ });
  return actor;
}
test("login reaches dashboard and logout clears the session", async () => {
  render(<App />);
  const actor = await login();
  await screen.findByRole("heading", { name: "Recent activity" });
  expect(localStorage.getItem("furocash_token")).toBe("valid-token");
  await actor.click(screen.getAllByRole("button", { name: /Log out/ })[0]);
  await screen.findByRole("heading", { name: "Good to see you again." });
  expect(localStorage.getItem("furocash_token")).toBeNull();
});
test("refresh restores the session without a PIN prompt", async () => {
  localStorage.setItem("furocash_token", "valid-token");
  render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
  await screen.findByRole("heading", { name: /Hello, Demo/ });
  expect(calls.some((c) => c.path === "/api/auth/me")).toBe(true);
});
test("a temporary restore failure can be retried", async () => {
  localStorage.setItem("furocash_token", "valid-token");
  meStatus = 503;
  render(<App />);
  await screen.findByRole("heading", { name: "Let’s reconnect." });
  expect(localStorage.getItem("furocash_token")).toBe("valid-token");
  meStatus = 200;
  await userEvent.click(screen.getByRole("button", { name: "Try again" }));
  await screen.findByRole("heading", { name: /Hello, Demo/ });
});
test("expired restore returns to login with an explanation", async () => {
  localStorage.setItem("furocash_token", "expired");
  meStatus = 401;
  render(<App />);
  await screen.findByText("Your session ended. Please log in again.");
  expect(localStorage.getItem("furocash_token")).toBeNull();
});
test("registration validates matching PINs and verifies the OTP", async () => {
  window.location.hash = "/register";
  render(<App />);
  const actor = userEvent.setup();
  await actor.type(screen.getByLabelText("Full name"), "New User");
  await actor.type(screen.getByLabelText("Mobile number"), "09171234567");
  await actor.type(screen.getByLabelText("Create PIN"), "1234");
  await actor.type(screen.getByLabelText("Confirm PIN"), "4321");
  await actor.click(screen.getByRole("button", { name: /Continue/ }));
  expect(screen.getByRole("alert").textContent).toContain("do not match");
  expect(calls).toHaveLength(0);
  await actor.clear(screen.getByLabelText("Confirm PIN"));
  await actor.type(screen.getByLabelText("Confirm PIN"), "1234");
  await actor.click(screen.getByRole("button", { name: /Continue/ }));
  await screen.findByLabelText("Verification code");
  expect(screen.queryByText(/Java server terminal/)).toBeNull();
  await screen.findByText(
    "Check your phone's SMS inbox. Messages can take a moment to arrive.",
  );
  await actor.type(screen.getByLabelText("Verification code"), "654321");
  await actor.click(
    screen.getByRole("button", { name: /Verify & create account/ }),
  );
  await screen.findByRole("heading", { name: /Hello, New/ });
  expect(calls.find((c) => c.path === "/api/auth/register")?.body?.otp).toBe(
    "654321",
  );
});
test("PIN recovery submits code and new PIN before returning to login", async () => {
  window.location.hash = "/forgot-pin";
  render(<App />);
  const actor = userEvent.setup();
  await actor.type(screen.getByLabelText("Mobile number"), "09171234567");
  await actor.click(screen.getByRole("button", { name: /Send recovery code/ }));
  await screen.findByLabelText("Recovery code");
  expect(screen.queryByText(/Java server terminal/)).toBeNull();
  await screen.findByText(
    "Check your phone's SMS inbox for the recovery code.",
  );
  await actor.type(screen.getByLabelText("Recovery code"), "654321");
  await actor.type(screen.getByLabelText("New PIN"), "5678");
  await actor.type(screen.getByLabelText("Confirm PIN"), "5678");
  await actor.click(screen.getByRole("button", { name: /Reset PIN/ }));
  await screen.findByText("PIN updated. Log in with your new PIN.");
  expect(calls.find((c) => c.path === "/api/auth/reset-pin")?.body).toEqual({
    mobile: "09171234567",
    otp: "654321",
    pin: "5678",
  });
});
test("demo funding updates balance and history with an exact amount", async () => {
  render(<App />);
  const actor = await login();
  const button = await screen.findByRole("link", { name: "Deposit" });
  await actor.click(button);
  await actor.type(screen.getByLabelText("Amount (PHP)"), "25.50");
  await actor.click(screen.getByRole("button", { name: "Review deposit" }));
  await actor.click(
    await screen.findByRole("button", { name: "Simulate completion" }),
  );
  await screen.findByRole("heading", { name: "Deposit complete" });
  await actor.click(
    screen.getByRole("link", { name: /View transaction history/ }),
  );
  await screen.findByText("Balance ₱125.50");
  expect(calls.find((c) => c.path === "/api/deposits/demo")?.body?.amount).toBe(
    "25.50",
  );
});

test("an interrupted funding response warns before a retry", async () => {
  render(<App />);
  const actor = await login();
  const button = await screen.findByRole("link", { name: "Deposit" });
  const realFetch = globalThis.fetch;
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: string, options: RequestInit = {}) =>
      String(input).endsWith("/complete")
        ? ({
            ok: true,
            status: 200,
            text: async () => {
              throw new TypeError("connection terminated");
            },
          } as Response)
        : realFetch(input, options),
    ),
  );
  await actor.click(button);
  await actor.type(screen.getByLabelText("Amount (PHP)"), "25.50");
  await actor.click(screen.getByRole("button", { name: "Review deposit" }));
  await actor.click(
    await screen.findByRole("button", { name: "Simulate completion" }),
  );
  await waitFor(() =>
    expect(screen.getByRole("alert").textContent).toContain(
      "may have completed",
    ),
  );
});

test("a failed registration SMS keeps the user on registration", async () => {
  window.location.hash = "/register";
  smsError = true;
  render(<App />);
  const actor = userEvent.setup();
  await actor.type(screen.getByLabelText("Full name"), "New User");
  await actor.type(screen.getByLabelText("Mobile number"), "09171234567");
  await actor.type(screen.getByLabelText("Create PIN"), "1234");
  await actor.type(screen.getByLabelText("Confirm PIN"), "1234");
  await actor.click(screen.getByRole("button", { name: /Continue/ }));
  await screen.findByRole("alert");
  expect(screen.getByRole("alert").textContent).toContain("SMS provider");
  expect(screen.queryByLabelText("Verification code")).toBeNull();
  expect(localStorage.getItem("furocash_token")).toBeNull();
});
test("a failed recovery SMS cannot advance to PIN reset", async () => {
  window.location.hash = "/forgot-pin";
  smsError = true;
  render(<App />);
  const actor = userEvent.setup();
  await actor.type(screen.getByLabelText("Mobile number"), "09171234567");
  await actor.click(screen.getByRole("button", { name: /Send recovery code/ }));
  await screen.findByRole("alert");
  expect(screen.getByRole("alert").textContent).toContain("SMS provider");
  expect(screen.queryByLabelText("Recovery code")).toBeNull();
});
