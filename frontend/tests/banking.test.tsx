import { afterEach, beforeEach, expect, test, vi } from "vitest";
import {
  render,
  screen,
  cleanup,
  fireEvent,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "../src/App";

const user = {
  id: 1,
  fullName: "Demo User",
  mobileNumber: "09171234567",
  balance: 100,
  accountIdentifier: "FC-0000000001",
  email: null,
  createdAt: "2026-10-19T00:00:00",
};
let calls: { path: string; body: Record<string, string>; key: string | null }[];
let failCompletion: boolean;
let cardExists: boolean;
const card = {
  id: "12345678-1234-4234-8234-123456789abc",
  identifier: "DEMO-FC-123456",
  maskedIdentifier: "DEMO-FC-••3456",
  cardholderName: user.fullName,
  status: "ACTIVE",
  dailyLimit: 1000,
  spentToday: 0,
  expiresOn: "2029-10-19",
  createdAt: "2026-10-19T00:00:00",
};
const receipt = {
  reference: "12345678-1234-4234-8234-123456789abc",
  kind: "DEPOSIT",
  status: "PENDING",
  amount: 25.5,
  balance: 100,
  accountIdentifier: user.accountIdentifier,
  counterparty: "GCASH_DEMO",
  note: "",
  createdAt: "2026-10-19T00:00:00",
};
beforeEach(() => {
  calls = [];
  failCompletion = false;
  cardExists = false;
  localStorage.setItem("furocash_token", "test");
  window.history.replaceState(null, "", "#/home");
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, options: RequestInit = {}) => {
      const path = new URL(url, "http://localhost").pathname;
      const body = options.body ? JSON.parse(String(options.body)) : {};
      calls.push({
        path,
        body,
        key: new Headers(options.headers).get("Idempotency-Key"),
      });
      let data: unknown = {};
      if (path === "/api/auth/me" || path === "/api/users/me")
        data = options.method === "PATCH" ? { ...user, ...body } : user;
      else if (path === "/api/account/balance") data = { balance: 100 };
      else if (path === "/api/transactions" || path.endsWith("/transactions"))
        data = [];
      else if (path === "/api/health") data = { demoCashIn: true };
      else if (path === "/api/deposits/options")
        data = {
          enabled: true,
          minAmount: 0.01,
          manualMaxAmount: 50000,
          fee: 0,
          methods: [
            { value: "BANK_TRANSFER_DEMO", label: "Bank Transfer (Demo)" },
          ],
        };
      else if (path === "/api/deposits/demo") data = receipt;
      else if (path.endsWith("/complete")) {
        if (failCompletion) throw new TypeError("Disconnected");
        data = { ...receipt, status: "COMPLETED", balance: 125.5 };
      } else if (path === "/api/transfers/validate-recipient")
        data = {
          accountIdentifier: "FC-0000000002",
          fullName: "Alex Rivera",
          mobileNumber: "09181234567",
        };
      else if (path === "/api/transfers/review")
        data = {
          reference: receipt.reference,
          recipient: {
            accountIdentifier: "FC-0000000002",
            fullName: "Alex Rivera",
            mobileNumber: "09181234567",
          },
          amount: Number(body.amount),
          fee: 0,
          total: Number(body.amount),
          balance: 100,
          note: body.note ?? "",
          qrReference: body.qrReference ?? null,
          expiresAt: "2099-10-20T12:00:00",
        };
      else if (path === "/api/transfers")
        data = {
          ...receipt,
          kind: "TRANSFER",
          status: "COMPLETED",
          counterparty: "Alex Rivera",
          balance: 74.5,
        };
      else if (path === "/api/cards/my") data = cardExists ? card : null;
      else if (path === "/api/cards") {
        cardExists = true;
        data = card;
      } else if (path.endsWith("/simulate-purchase"))
        data = {
          ...receipt,
          kind: "CARD_PURCHASE",
          status: "COMPLETED",
          balance: 74.5,
        };
      else if (path === "/api/qr/resolve")
        data = {
          recipient: {
            accountIdentifier: "FC-0000000002",
            fullName: "Alex Rivera",
            mobileNumber: "09181234567",
          },
          amount: "25.50",
          reference: receipt.reference,
          purpose: "TRANSFER",
        };
      return new Response(JSON.stringify(data), { status: 200 });
    }),
  );
});
afterEach(() => {
  cleanup();
  localStorage.clear();
  localStorage.clear();
  window.history.replaceState(null, "", "/");
  vi.unstubAllGlobals();
});

test("dark mode works across pages and persists after reopening the app", async () => {
  const view = render(<App />);
  await screen.findByRole("heading", { name: /Hello, Demo/ });
  await userEvent.click(
    screen.getByRole("button", { name: "Switch to dark mode" }),
  );
  expect(document.documentElement.dataset.theme).toBe("dark");
  expect(localStorage.getItem("furocash_theme")).toBe("dark");
  await userEvent.click(screen.getAllByRole("link", { name: "Me" })[0]);
  await screen.findByRole("heading", { name: "Your profile" });
  expect(
    screen.getByRole("button", { name: "Switch to light mode" }),
  ).toBeTruthy();
  view.unmount();
  render(<App />);
  await screen.findByRole("heading", { name: "Your profile" });
  expect(document.documentElement.dataset.theme).toBe("dark");
  await userEvent.click(
    screen.getByRole("button", { name: "Switch to light mode" }),
  );
  expect(document.documentElement.dataset.theme).toBe("light");
});

test("manual GCash demo supports presets and custom amount before explicit completion", async () => {
  render(<App />);
  await screen.findByRole("heading", { name: /Hello, Demo/ });
  await userEvent.click(screen.getByRole("link", { name: "Deposit" }));
  await screen.findByRole("heading", { name: "Manual demo deposit" });
  await userEvent.click(screen.getByRole("button", { name: "Add 500 pesos" }));
  const input = screen.getByLabelText("Amount (PHP)") as HTMLInputElement;
  expect(input.value).toBe("500.00");
  await userEvent.clear(input);
  await userEvent.type(input, "25.50");
  await userEvent.click(screen.getByRole("button", { name: "Review deposit" }));
  await screen.findByRole("heading", { name: "Review deposit" });
  expect(
    calls.find((call) => call.path === "/api/deposits/demo")?.body,
  ).toEqual({ amount: "25.50", method: "GCASH_DEMO" });
  expect(calls.filter((call) => call.path.endsWith("/complete"))).toHaveLength(
    0,
  );
  expect(screen.getByText("Demo service fee")).toBeTruthy();
  await userEvent.click(
    screen.getByRole("button", { name: "Simulate completion" }),
  );
  await screen.findByRole("heading", { name: "Deposit complete" });
});

test("slow session requests show live progress until response completes", async () => {
  const originalFetch = globalThis.fetch;
  let release!: (value: Response) => void;
  const response = new Promise<Response>((resolve) => {
    release = resolve;
  });
  vi.stubGlobal(
    "fetch",
    vi.fn((url: string, options?: RequestInit) =>
      String(url).endsWith("/api/auth/me")
        ? response
        : originalFetch(url, options),
    ),
  );
  render(<App />);
  await screen.findByRole("progressbar", { name: "Loading FuroCash" });
  expect(screen.queryByRole("heading", { name: /Hello, Demo/ })).toBeNull();
  release(new Response(JSON.stringify(user), { status: 200 }));
  await screen.findByRole("heading", { name: /Hello, Demo/ });
  await waitFor(() =>
    expect(
      screen.queryByRole("progressbar", { name: "Loading FuroCash" }),
    ).toBeNull(),
  );
});
test("shared navigation exposes five destinations and routes to profile", async () => {
  render(<App />);
  await screen.findByRole("heading", { name: /Hello, Demo/ });
  const nav = screen.getByRole("navigation", { name: "Main navigation" });
  expect(nav.querySelectorAll("a")).toHaveLength(5);
  await userEvent.click(screen.getAllByRole("link", { name: "Me" })[0]);
  await screen.findByRole("heading", { name: "Your profile" });
  expect(screen.getByDisplayValue(user.fullName)).toBeTruthy();
});
test("deposit prepares without moving money and completion retries keep the same key", async () => {
  render(<App />);
  await screen.findByRole("heading", { name: /Hello, Demo/ });
  await userEvent.click(screen.getByRole("link", { name: "Deposit" }));
  await userEvent.type(screen.getByLabelText("Amount (PHP)"), "25.50");
  await userEvent.click(screen.getByRole("button", { name: "Review deposit" }));
  await screen.findByRole("heading", { name: "Review deposit" });
  expect(calls.filter((c) => c.path.endsWith("/complete"))).toHaveLength(0);
  failCompletion = true;
  await userEvent.click(
    screen.getByRole("button", { name: "Simulate completion" }),
  );
  await screen.findByRole("alert");
  failCompletion = false;
  await userEvent.click(
    screen.getByRole("button", { name: "Simulate completion" }),
  );
  await screen.findByRole("heading", { name: "Deposit complete" });
  const completions = calls.filter((c) => c.path.endsWith("/complete"));
  expect(completions[0].key).toBeTruthy();
  expect(completions[0].key).toBe(completions[1].key);
  expect(calls.find((c) => c.path === "/api/deposits/demo")?.body.amount).toBe(
    "25.50",
  );
});
test("transfer resolves the recipient and only sends on explicit confirmation", async () => {
  location.hash = "/transfer";
  render(<App />);
  await screen.findByLabelText("Account identifier or mobile");
  await userEvent.type(
    screen.getByLabelText("Account identifier or mobile"),
    "FC-0000000002",
  );
  await userEvent.click(screen.getByRole("button", { name: "Find recipient" }));
  await screen.findByText("Alex Rivera");
  await userEvent.type(screen.getByLabelText("Amount (PHP)"), "25.50");
  await userEvent.click(
    screen.getByRole("button", { name: "Review Transfer" }),
  );
  await screen.findByRole("heading", { name: "Review Transfer" });
  expect(calls.filter((c) => c.path === "/api/transfers")).toHaveLength(0);
  await userEvent.click(
    screen.getByRole("button", { name: "Confirm Transfer" }),
  );
  await screen.findByRole("heading", { name: "Transfer Successful" });
  expect(
    calls.find((c) => c.path === "/api/transfers")?.body.receiverAccount,
  ).toBe("FC-0000000002");
});
test("authenticated users can reach existing SMS PIN recovery", async () => {
  location.hash = "/forgot-pin";
  render(<App />);
  await screen.findByRole("button", { name: /Send recovery code/ });
});
test("card creation is confirmed and identical new purchases get distinct keys", async () => {
  location.hash = "/card";
  render(<App />);
  await userEvent.click(
    await screen.findByRole("button", { name: "Create demo card" }),
  );
  expect(calls.filter((c) => c.path === "/api/cards")).toHaveLength(0);
  await userEvent.click(screen.getByRole("button", { name: "Confirm" }));
  await screen.findByText("DEMO-FC-••3456");
  expect(screen.queryByText(card.identifier)).toBeNull();
  for (let i = 0; i < 2; i++) {
    await userEvent.type(screen.getByLabelText("Demo merchant"), "Coffee");
    await userEvent.type(
      screen.getByLabelText("Purchase amount (PHP)"),
      "25.50",
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Review demo purchase" }),
    );
    expect(
      calls.filter((c) => c.path.endsWith("/simulate-purchase")),
    ).toHaveLength(i);
    await userEvent.click(screen.getByRole("button", { name: "Confirm" }));
    await screen.findByRole("heading", { name: "Demo purchase complete" });
    await userEvent.click(screen.getByRole("button", { name: "Back to card" }));
  }
  const purchases = calls.filter((c) => c.path.endsWith("/simulate-purchase"));
  expect(purchases[0].key).not.toBe(purchases[1].key);
});
test("profile saves live details and keeps phone read-only", async () => {
  location.hash = "/me";
  render(<App />);
  const name = await screen.findByLabelText("Full name");
  expect(
    screen.getByLabelText("Verified mobile number").hasAttribute("readonly"),
  ).toBe(true);
  await userEvent.clear(name);
  await userEvent.type(name, "Updated User");
  await userEvent.click(screen.getByRole("button", { name: "Save profile" }));
  await screen.findByText("Profile saved.");
  expect(
    calls.find((c) => c.path === "/api/users/me" && c.body.fullName)?.body
      .fullName,
  ).toBe("Updated User");
});
test("QR resolves persisted data and requires confirmation with fixed amount", async () => {
  location.hash = "/scan";
  render(<App />);
  await screen.findByRole("heading", { name: "Scan & Pay" });
  expect(screen.queryByRole("button", { name: "Stop camera" })).toBeNull();
  await userEvent.click(screen.getByText("Paste QR contents"));
  const payload = {
    app: "FUROCASH",
    version: 1,
    purpose: "TRANSFER",
    accountIdentifier: "FC-0000000002",
    reference: receipt.reference,
    amount: "25.50",
  };
  fireEvent.change(screen.getByLabelText("FuroCash QR JSON"), {
    target: { value: JSON.stringify(payload) },
  });
  await userEvent.click(screen.getByRole("button", { name: "Read QR" }));
  await screen.findByLabelText("Amount (PHP)");
  expect(screen.getByLabelText("Amount (PHP)").hasAttribute("readonly")).toBe(
    true,
  );
  expect(calls.filter((c) => c.path === "/api/qr/resolve")).toHaveLength(1);
  expect(calls.filter((c) => c.path === "/api/transfers")).toHaveLength(0);
  await userEvent.click(
    screen.getByRole("button", { name: "Review Transfer" }),
  );
  await userEvent.click(await screen.findByRole("button", { name: "Confirm Transfer" }));
  await screen.findByRole("heading", { name: "Transfer Successful" });
  expect(calls.find((c) => c.path === "/api/transfers")?.body.qrReference).toBe(
    receipt.reference,
  );
});

test("skip link focuses content without changing the current route", async () => {
  location.hash = "/me";
  render(<App />);
  await screen.findByLabelText("Full name");
  await userEvent.click(screen.getByRole("link", { name: "Skip to content" }));
  expect(location.hash).toBe("#/me");
  expect(document.activeElement).toBe(document.getElementById("main-content"));
});

test("card confirmation traps focus, makes background inert and restores focus on Escape", async () => {
  location.hash = "/card";
  const view = render(<App />);
  const trigger = await screen.findByRole("button", {
    name: "Create demo card",
  });
  await userEvent.click(trigger);
  expect(view.container.inert).toBe(true);
  const confirm = screen.getByRole("button", { name: "Confirm" });
  const cancel = screen.getByRole("button", { name: "Cancel" });
  expect(document.activeElement).toBe(confirm);
  await userEvent.tab({ shift: true });
  expect(document.activeElement).toBe(cancel);
  await userEvent.tab();
  expect(document.activeElement).toBe(confirm);
  await userEvent.keyboard("{Escape}");
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(view.container.inert).toBeFalsy();
  expect(document.activeElement).toBe(trigger);
  expect(calls.some((c) => c.path === "/api/cards")).toBe(false);
});

test("profile response arriving after logout cannot restore the old session", async () => {
  const original = globalThis.fetch;
  let resolveSave!: (response: Response) => void;
  vi.stubGlobal(
    "fetch",
    vi.fn((url: string, options: RequestInit = {}) =>
      options.method === "PATCH"
        ? new Promise<Response>((resolve) => {
            resolveSave = resolve;
          })
        : original(url, options),
    ),
  );
  location.hash = "/me";
  render(<App />);
  await screen.findByLabelText("Full name");
  await userEvent.click(screen.getByRole("button", { name: "Save profile" }));
  await userEvent.click(screen.getAllByRole("button", { name: /Log out/ })[0]);
  await screen.findByRole("heading", { name: "Good to see you again." });
  resolveSave(
    new Response(JSON.stringify({ ...user, fullName: "Stale User" })),
  );
  await new Promise((resolve) => setTimeout(resolve, 0));
  expect(localStorage.getItem("furocash_token")).toBeNull();
  expect(screen.queryByRole("heading", { name: /Hello/ })).toBeNull();
  expect(
    screen.getByRole("heading", { name: "Good to see you again." }),
  ).toBeTruthy();
});

test("leaving a QR transfer clears its fixed recipient before manual entry", async () => {
  location.hash = "/scan";
  render(<App />);
  await screen.findByRole("heading", { name: "Scan & Pay" });
  expect(
    screen.getByRole("link", { name: "Enter account details manually" }),
  ).toBeTruthy();
  await userEvent.click(screen.getByText("Paste QR contents"));
  fireEvent.change(screen.getByLabelText("FuroCash QR JSON"), {
    target: {
      value: JSON.stringify({
        app: "FUROCASH",
        version: 1,
        purpose: "TRANSFER",
        accountIdentifier: "FC-0000000002",
        reference: receipt.reference,
        amount: "25.50",
      }),
    },
  });
  await userEvent.click(screen.getByRole("button", { name: "Read QR" }));
  const fixed = await screen.findByLabelText("Amount (PHP)");
  expect(fixed.hasAttribute("readonly")).toBe(true);
  await userEvent.click(screen.getAllByRole("link", { name: "Scan & Pay" })[0]);
  await screen.findByRole("heading", { name: "Scan & Pay" });
  await userEvent.click(
    screen.getByRole("link", { name: "Enter account details manually" }),
  );
  const recipient = await screen.findByLabelText(
    "Account identifier or mobile",
  );
  expect((recipient as HTMLInputElement).value).toBe("");
  expect((recipient as HTMLInputElement).disabled).toBe(false);
  expect(screen.queryByLabelText("Amount (PHP)")).toBeNull();
});
