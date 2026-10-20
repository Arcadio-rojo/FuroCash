import { beforeEach, afterEach, test, expect, vi } from "vitest";
import {
  render,
  screen,
  within,
  cleanup,
  waitFor,
  fireEvent,
  act,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "../src/App";

const user = {
  id: 1,
  fullName: "Demo Sender",
  mobileNumber: "09171234567",
  balance: 2000,
  accountIdentifier: "FC-0000000001",
  email: null,
  createdAt: "2026-10-20T10:00:00",
};
const recipient = {
  accountIdentifier: "FC-0000000002",
  fullName: "Juan Demo",
  mobileNumber: "09181234567",
};
const prepared = {
  reference: "DEP-confirmed-demo",
  kind: "DEPOSIT",
  status: "PENDING",
  amount: 1000,
  balance: 2000,
  accountIdentifier: user.accountIdentifier,
  counterparty: "Bank Transfer (Demo)",
  note: "Savings",
  createdAt: "2026-10-20T10:00:00",
  fee: 0,
  total: 1000,
  method: "Bank Transfer (Demo)",
  recipientAccount: user.accountIdentifier,
  recipientName: user.fullName,
};
const quote = {
  reference: "12345678-1234-4234-8234-123456789abc",
  recipient,
  amount: 500,
  fee: 5,
  total: 505,
  balance: 2000,
  note: "Lunch",
  qrReference: null,
  expiresAt: "2099-10-20T11:00:00",
};
let calls: {
  path: string;
  method: string;
  body: Record<string, string>;
  key: string | null;
}[];
let enabled: boolean;
let loseReply: boolean;
let reviewChanged: boolean;
let rejectDeposit: boolean;
let recovery: string;
let wallet: number;
const reply = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status });
function completed(kind = "DEPOSIT") {
  return kind === "DEPOSIT"
    ? {
        ...prepared,
        status: "COMPLETED",
        balance: 3000,
        createdAt: "2026-10-20T10:01:02",
      }
    : {
        ...prepared,
        kind: "TRANSFER",
        reference: "FC-transfer-confirmed",
        status: "COMPLETED",
        amount: 500,
        fee: 5,
        total: 505,
        balance: 1495,
        recipientAccount: recipient.accountIdentifier,
        recipientName: recipient.fullName,
        counterparty: recipient.fullName,
        method: "FuroCash Wallet Transfer (Demo)",
      };
}
beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  localStorage.setItem("furocash_token", "test");
  window.history.replaceState(null, "", "#/deposit");
  calls = [];
  enabled = true;
  loseReply = false;
  reviewChanged = false;
  rejectDeposit = false;
  recovery = "NOT_FOUND";
  wallet = 2000;
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, options: RequestInit = {}) => {
      const path = new URL(url, "http://localhost").pathname,
        body = options.body ? JSON.parse(String(options.body)) : {};
      calls.push({
        path,
        method: options.method ?? "GET",
        body,
        key: new Headers(options.headers).get("Idempotency-Key"),
      });
      if (path === "/api/auth/me") return reply(user);
      if (path === "/api/account/balance") return reply({ balance: wallet });
      if (path === "/api/transactions") return reply([]);
      if (path === "/api/health") return reply({ demoCashIn: enabled });
      if (path === "/api/deposits/options")
        return reply({
          enabled,
          minAmount: 0.01,
          manualMaxAmount: 50000,
          fee: 0,
          methods: [
            { value: "CASH_DEMO", label: "Cash Deposit (Demo)" },
            { value: "BANK_TRANSFER_DEMO", label: "Bank Transfer (Demo)" },
            { value: "EWALLET_DEMO", label: "E-Wallet (Demo)" },
            { value: "OTHER_DEMO", label: "Other (Demo)" },
          ],
        });
      if (path === "/api/deposits/demo") return reply(prepared, 201);
      if (path.startsWith("/api/operations/"))
        return reply({
          state: recovery,
          receipt: recovery === "COMPLETED" ? completed() : null,
        });
      if (path.endsWith("/complete")) {
        if (rejectDeposit)
          return reply({ error: "Deposit details need another review" }, 409);
        if (loseReply) throw new TypeError("Lost response");
        wallet = 3000;
        return reply(completed());
      }
      if (path === "/api/transfers/validate-recipient") return reply(recipient);
      if (path === "/api/transfers/review") return reply(quote);
      if (path === "/api/transfers") {
        if (reviewChanged)
          return reply(
            { error: "Transfer details changed. Review again" },
            409,
          );
        wallet = 1495;
        return reply(completed("TRANSFER"));
      }
      return reply({});
    }),
  );
});
afterEach(() => {
  vi.useRealTimers();
  cleanup();
  localStorage.clear();
  sessionStorage.clear();
  window.history.replaceState(null, "", "/");
  vi.unstubAllGlobals();
});
async function manual() {
  render(<App />);
  const form = within(
    await screen.findByRole("region", { name: "Manual Deposit" }),
  );
  if (enabled) await form.findByRole("button", { name: "Review Deposit" });
  return form;
}

async function preparedManual() {
  const form = await manual();
  await userEvent.type(form.getByLabelText("Deposit amount (PHP)"), "1000");
  await userEvent.selectOptions(
    form.getByLabelText("Deposit method"),
    "BANK_TRANSFER_DEMO",
  );
  await userEvent.click(form.getByRole("button", { name: "Review Deposit" }));
  await form.findByRole("heading", { name: "Review Deposit" });
  return form;
}
test("double confirmation clicks submit once while the backend is pending", async () => {
  const form = await preparedManual();
  const original = globalThis.fetch;
  let release!: () => void;
  let submissions = 0;
  vi.stubGlobal("fetch", (url: string, options?: RequestInit) =>
    String(url).endsWith("/complete")
      ? new Promise<Response>((resolve) => {
          submissions++;
          release = () => {
            void original(url, options).then(resolve);
          };
        })
      : original(url, options),
  );
  const confirm = form.getByRole("button", { name: "Confirm Demo Deposit" });
  fireEvent.click(confirm);
  fireEvent.click(confirm);
  expect(submissions).toBe(1);
  expect(
    (
      form.getByRole("button", {
        name: "Check transaction status",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true);
  release();
  await form.findByRole("heading", { name: "Deposit Successful" });
});
test("timed-out confirmations keep the journal and replay the same key safely", async () => {
  const form = await preparedManual();
  const original = globalThis.fetch;
  vi.stubGlobal("fetch", (url: string, options?: RequestInit) =>
    String(url).endsWith("/complete")
      ? new Promise<Response>((_, reject) => {
          options?.signal?.addEventListener(
            "abort",
            () => reject(new DOMException("Timeout", "AbortError")),
            { once: true },
          );
        })
      : original(url, options),
  );
  vi.useFakeTimers();
  fireEvent.click(form.getByRole("button", { name: "Confirm Demo Deposit" }));
  await act(async () => {
    await vi.advanceTimersByTimeAsync(30000);
  });
  expect(form.getByText(/The outcome is not confirmed yet/)).toBeTruthy();
  const saved = JSON.parse(sessionStorage.getItem("furocash_money_1")!);
  expect(saved.key).toBeTruthy();
  vi.useRealTimers();
  vi.stubGlobal("fetch", original);
  await userEvent.click(
    form.getByRole("button", { name: "Retry same request" }),
  );
  await form.findByRole("heading", { name: "Deposit Successful" });
  expect(calls.filter((c) => c.path.endsWith("/complete")).at(-1)?.key).toBe(
    saved.key,
  );
  expect(sessionStorage.getItem("furocash_money_1")).toBeNull();
});
test("manual deposit validates limits, preserves existing methods, reviews before credit, and confirms", async () => {
  const form = await manual();
  expect(screen.getByRole("heading", { name: "GCash demo" })).toBeTruthy();
  expect(
    screen.getByRole("heading", { name: "Your demo deposit QR" }),
  ).toBeTruthy();
  expect(
    (form.getByRole("button", { name: "Review Deposit" }) as HTMLButtonElement)
      .disabled,
  ).toBe(true);
  await userEvent.type(form.getByLabelText("Deposit amount (PHP)"), "50000.01");
  await userEvent.selectOptions(
    form.getByLabelText("Deposit method"),
    "BANK_TRANSFER_DEMO",
  );
  expect(
    (form.getByRole("button", { name: "Review Deposit" }) as HTMLButtonElement)
      .disabled,
  ).toBe(true);
  await userEvent.clear(form.getByLabelText("Deposit amount (PHP)"));
  await userEvent.type(form.getByLabelText("Deposit amount (PHP)"), "1000");
  await userEvent.type(form.getByLabelText("Notes (optional)"), "Savings");
  await userEvent.click(form.getByRole("button", { name: "Review Deposit" }));
  await form.findByRole("heading", { name: "Review Deposit" });
  expect(calls.filter((c) => c.path.endsWith("/complete"))).toHaveLength(0);
  expect(wallet).toBe(2000);
  expect(calls.find((c) => c.path === "/api/deposits/demo")?.body).toEqual({
    amount: "1000",
    method: "BANK_TRANSFER_DEMO",
    note: "Savings",
  });
  await userEvent.click(
    form.getByRole("button", { name: "Confirm Demo Deposit" }),
  );
  await form.findByRole("heading", { name: "Deposit Successful" });
  expect(form.getByRole("button", { name: "Download Receipt" })).toBeTruthy();
  expect(wallet).toBe(3000);
});
test("manual funding remains unavailable when disabled", async () => {
  enabled = false;
  const form = await manual();
  await form.findByText(/Manual deposits are disabled/);
  expect(
    form.queryByRole("button", { name: "Confirm Demo Deposit" }),
  ).toBeNull();
  expect(calls.filter((c) => c.path === "/api/deposits/demo")).toHaveLength(0);
});
test("a rejected deposit can be edited and reviewed again before a fresh confirmation", async () => {
  const form = await manual();
  await userEvent.type(form.getByLabelText("Deposit amount (PHP)"), "1000");
  await userEvent.selectOptions(
    form.getByLabelText("Deposit method"),
    "BANK_TRANSFER_DEMO",
  );
  await userEvent.click(form.getByRole("button", { name: "Review Deposit" }));
  await form.findByRole("heading", { name: "Review Deposit" });
  rejectDeposit = true;
  await userEvent.click(
    form.getByRole("button", { name: "Confirm Demo Deposit" }),
  );
  await userEvent.click(
    await form.findByRole("button", { name: "Review details again" }),
  );
  await userEvent.click(form.getByRole("button", { name: "Review Deposit" }));
  await form.findByRole("heading", { name: "Review Deposit" });
  rejectDeposit = false;
  await userEvent.click(
    form.getByRole("button", { name: "Confirm Demo Deposit" }),
  );
  await form.findByRole("heading", { name: "Deposit Successful" });
});
test("transfer review uses backend fee and masked recipient before explicit confirmation", async () => {
  window.history.replaceState(null, "", "#/transfer");
  render(<App />);
  await userEvent.type(
    await screen.findByLabelText("Account identifier or mobile"),
    recipient.accountIdentifier,
  );
  await userEvent.click(screen.getByRole("button", { name: "Find recipient" }));
  await screen.findByText(recipient.fullName);
  await userEvent.type(screen.getByLabelText("Amount (PHP)"), "500");
  await userEvent.type(screen.getByLabelText("Note (optional)"), "Lunch");
  await userEvent.click(
    screen.getByRole("button", { name: "Review Transfer" }),
  );
  await screen.findByRole("heading", { name: "Review Transfer" });
  expect(calls.filter((c) => c.path === "/api/transfers")).toHaveLength(0);
  expect(
    screen.getByText("Total Deduction").nextElementSibling?.textContent,
  ).toContain("505");
  expect(screen.queryByText(recipient.accountIdentifier)).toBeNull();
  await userEvent.click(
    screen.getByRole("button", { name: "Confirm Transfer" }),
  );
  await screen.findByRole("heading", { name: "Transfer Successful" });
  expect(
    calls.find((c) => c.path === "/api/transfers")?.body.reviewReference,
  ).toBe(quote.reference);
  expect(wallet).toBe(1495);
});
test("an unknown deposit is recovered after remount without submitting another transaction", async () => {
  const view = render(<App />);
  const form = within(
    await screen.findByRole("region", { name: "Manual Deposit" }),
  );
  await form.findByLabelText("Deposit amount (PHP)");
  await userEvent.type(form.getByLabelText("Deposit amount (PHP)"), "1000");
  await userEvent.selectOptions(
    form.getByLabelText("Deposit method"),
    "BANK_TRANSFER_DEMO",
  );
  await userEvent.click(form.getByRole("button", { name: "Review Deposit" }));
  await form.findByRole("heading", { name: "Review Deposit" });
  loseReply = true;
  await userEvent.click(
    form.getByRole("button", { name: "Confirm Demo Deposit" }),
  );
  await form.findByRole("button", { name: "Check transaction status" });
  await form.findByText(/The outcome is not confirmed yet/);
  const original = calls.filter((c) => c.path.endsWith("/complete"));
  expect(original).toHaveLength(1);
  view.unmount();
  recovery = "COMPLETED";
  render(<App />);
  const restored = within(
    await screen.findByRole("region", { name: "Manual Deposit" }),
  );
  await userEvent.click(
    await restored.findByRole("button", { name: "Check transaction status" }),
  );
  await restored.findByRole("heading", { name: "Deposit Successful" });
  await waitFor(() =>
    expect(calls.filter((c) => c.path.endsWith("/complete"))).toHaveLength(1),
  );
});
