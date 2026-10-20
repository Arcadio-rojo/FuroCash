import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { render, screen, cleanup, fireEvent } from "@testing-library/react";
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
  id: 1,
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
  location.hash = "/home";
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
  vi.unstubAllGlobals();
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
    screen.getByRole("button", { name: "Review transfer" }),
  );
  await screen.findByRole("heading", { name: "Confirm transfer" });
  expect(calls.filter((c) => c.path === "/api/transfers")).toHaveLength(0);
  await userEvent.click(screen.getByRole("button", { name: "Confirm & send" }));
  await screen.findByRole("heading", { name: "Transfer complete" });
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
    screen.getByRole("button", { name: "Review transfer" }),
  );
  await userEvent.click(screen.getByRole("button", { name: "Confirm & send" }));
  await screen.findByRole("heading", { name: "Transfer complete" });
  expect(calls.find((c) => c.path === "/api/transfers")?.body.qrReference).toBe(
    receipt.reference,
  );
});
