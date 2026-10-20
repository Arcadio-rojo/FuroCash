// @vitest-environment jsdom
import { afterEach, expect, test, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { PaymentReceipt } from "../src/components/BankingUi";
import TransactionList from "../src/components/TransactionList";
import TransactionReceipt from "../src/components/TransactionReceipt";
import type { Receipt, Transaction } from "../src/api";

const receipt = {
  reference: "receipt-server-123",
  kind: "TRANSFER_OUT",
  status: "COMPLETED",
  amount: 250,
  balance: 700,
  accountIdentifier: "FC-1234567890",
  counterparty: "Recipient",
  note: "Lunch",
  createdAt: "2026-10-20T06:00:00Z",
  fee: 0,
  total: 250,
  senderAccount: "FC-1234567890",
  senderName: "Sender",
  recipientAccount: "FC-9876543210",
  recipientName: "Recipient",
  method: null,
} as Receipt;
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

test.each([
  ["TRANSFER", "−₱250.00", "Transfer Fee", "Total Deducted"],
  ["TRANSFER_OUT", "−₱250.00", "Transfer Fee", "Total Deducted"],
  ["CARD_PURCHASE", "−₱250.00", "Processing Fee", "Total Deducted"],
  ["DEPOSIT", "+₱250.00", "Processing Fee", "Total Credit"],
  ["TRANSFER_IN", "+₱250.00", "Transfer Fee", "Total Credit"],
])(
  "%s shows signed amounts, contextual labels and accessible completed status",
  (kind, amount, fee, total) => {
    render(<TransactionReceipt receipt={{ ...receipt, kind }} />);
    expect(screen.getByText(amount)).toBeTruthy();
    expect(screen.getByText(fee)).toBeTruthy();
    expect(screen.getByText(total)).toBeTruthy();
    expect(screen.getByText("f.")).toBeTruthy();
    expect(screen.getByRole("status").textContent).toContain(
      "completed successfully",
    );
    expect(
      screen.getByRole("button", { name: "Download Receipt" }),
    ).toBeTruthy();
    expect(screen.getByRole("button", { name: "Share Receipt" })).toBeTruthy();
  },
);

test("completed receipts mask accounts and expose only supplied fee and total", () => {
  const { container } = render(
    <PaymentReceipt receipt={receipt} title="Money sent" />,
  );
  expect(container.textContent).not.toContain("FC-1234567890");
  expect(container.textContent).not.toContain("FC-9876543210");
  expect(container.textContent).toContain("7890");
  expect(container.textContent).toContain("3210");
  expect(screen.getByText("Transfer Fee")).toBeTruthy();
  expect(screen.getByText("Total Deducted")).toBeTruthy();
  expect(screen.getByText("DEMO — NOT PROOF OF PAYMENT")).toBeTruthy();
  expect(screen.getByRole("button", { name: "Download Receipt" })).toBeTruthy();
});

test.each(["PENDING", "UNKNOWN"])(
  "%s cannot present success or export controls",
  (status) => {
    render(
      <PaymentReceipt
        receipt={{ ...receipt, status } as Receipt}
        title="Money sent"
      />,
    );
    expect(screen.queryByRole("heading", { name: "Money sent" })).toBeNull();
    expect(
      screen.queryByRole("button", { name: "Download Receipt" }),
    ).toBeNull();
    expect(screen.getByRole("status").textContent).toContain("not completed");
  },
);

test("legacy card receipt omits missing optional metadata and retains title", () => {
  const legacy = {
    reference: "card-ref",
    kind: "CARD_PURCHASE",
    status: "COMPLETED",
    amount: 40,
    balance: 660,
    accountIdentifier: "FC-1234567890",
    counterparty: "Cafe",
    note: null,
    createdAt: "2026-10-20T06:00:00Z",
  } as Receipt;
  render(<PaymentReceipt receipt={legacy} title="Purchase complete" />);
  expect(
    screen.getByRole("heading", { name: "Purchase complete" }),
  ).toBeTruthy();
  expect(screen.queryByText("Processing Fee")).toBeNull();
  expect(screen.queryByText("Total Deducted")).toBeNull();
});

const tx = {
  id: 1,
  type: "TRANSFER_IN",
  status: "COMPLETED",
  amount: 250,
  details: "Received",
  referenceNo: "receipt-server-123",
  balanceAfter: 700,
  createdAt: "2026-10-20T06:00:00Z",
  receiptAvailable: true,
} as Transaction;

test("history opens an owned server receipt and returns to the original list", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      expect(url).toBe("/api/transactions/receipt-server-123/receipt");
      return new Response(JSON.stringify({ ...receipt, kind: "TRANSFER_IN" }), {
        status: 200,
      });
    }),
  );
  render(<TransactionList transactions={[tx]} />);
  expect(screen.getByText("+₱250.00")).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "View receipt" }));
  expect(
    await screen.findByRole("heading", { name: "Money received" }),
  ).toBeTruthy();
  fireEvent.click(
    screen.getByRole("button", { name: "Back to transaction history" }),
  );
  expect(screen.getByText("Received")).toBeTruthy();
});

test("history does not fabricate receipts for unsupported or pending transactions", () => {
  render(
    <TransactionList
      transactions={[
        { ...tx, receiptAvailable: false },
        { ...tx, id: 2, status: "PENDING" } as unknown as Transaction,
      ]}
    />,
  );
  expect(screen.queryByRole("button", { name: "View receipt" })).toBeNull();
});

test("history recognizes the backend TRANSFER receipt kind as outgoing", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(
      async () =>
        new Response(
          JSON.stringify({ ...receipt, kind: "TRANSFER", method: "FUROCASH" }),
          { status: 200 },
        ),
    ),
  );
  render(<TransactionList transactions={[{ ...tx, type: "TRANSFER_OUT" }]} />);
  expect(screen.getByText("−₱250.00")).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "View receipt" }));
  expect(
    await screen.findByRole("heading", { name: "Money sent" }),
  ).toBeTruthy();
  expect(screen.getByText("FuroCash transfer")).toBeTruthy();
});

test.each(["missing", "pending"])(
  "history preserves its list when server receipt is %s",
  async (mode) => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        mode === "missing"
          ? new Response(JSON.stringify({ error: "Receipt unavailable" }), {
              status: 404,
            })
          : new Response(JSON.stringify({ ...receipt, status: "PENDING" }), {
              status: 200,
            }),
      ),
    );
    render(<TransactionList transactions={[tx]} />);
    fireEvent.click(screen.getByRole("button", { name: "View receipt" }));
    expect(await screen.findByRole("alert")).toBeTruthy();
    expect(screen.getByText("Received")).toBeTruthy();
    expect(
      screen.queryByRole("button", { name: "Download Receipt" }),
    ).toBeNull();
  },
);

test("download errors are shown without losing the completed receipt", async () => {
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(null);
  render(<PaymentReceipt receipt={receipt} title="Money sent" />);
  fireEvent.click(screen.getByRole("button", { name: "Download Receipt" }));
  expect(
    await screen.findByText("PNG export is unavailable in this browser."),
  ).toBeTruthy();
  expect(screen.getByRole("heading", { name: "Money sent" })).toBeTruthy();
  expect(
    (
      screen.getByRole("button", {
        name: "Download Receipt",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(false);
});
