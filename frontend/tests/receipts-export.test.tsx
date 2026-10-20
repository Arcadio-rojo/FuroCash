// @vitest-environment jsdom
import { afterEach, expect, test, vi } from "vitest";
import {
  createReceiptPng,
  receiptDetails,
  shareReceipt,
} from "../src/receiptExport";
import type { Receipt } from "../src/api";
const receipt = {
  reference: "server-ref",
  kind: "DEPOSIT",
  status: "COMPLETED",
  amount: 50,
  balance: 150,
  accountIdentifier: "FC-1234567890",
  counterparty: null,
  note: "For FC-1234567890",
  createdAt: "2026-10-20T06:00:00Z",
  fee: 0,
  total: 50,
  method: "CASH_DEMO",
} as Receipt;
afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
test("PNG carries the demo watermark, authoritative amounts and masked accounts", async () => {
  const painted: string[] = [];
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue({
    fillRect() {},
    fillText(text: string) {
      painted.push(text);
    },
    measureText(text: string) {
      return { width: text.length * 8 };
    },
  } as unknown as CanvasRenderingContext2D);
  vi.spyOn(HTMLCanvasElement.prototype, "toBlob").mockImplementation(
    (callback) => callback(new Blob(["png"], { type: "image/png" })),
  );
  const blob = await createReceiptPng(receipt);
  expect(blob.type).toBe("image/png");
  expect(painted.join(" ")).toContain("DEMO — NOT PROOF OF PAYMENT");
  expect(painted.join(" ")).not.toContain("FC-1234567890");
  expect(painted.join(" ")).toContain("7890");
  expect(painted.join(" ")).toContain("+₱50.00");
  expect(receiptDetails(receipt)).toContainEqual(["Processing Fee", "₱0.00"]);
});
test("pending receipts cannot be exported", async () => {
  await expect(
    createReceiptPng({ ...receipt, status: "PENDING" }),
  ).rejects.toThrow(/not completed/i);
});

test("PNG uses Money sent for the actual backend TRANSFER receipt kind", async () => {
  const painted: string[] = [];
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue({
    fillRect() {},
    fillText(value: string) {
      painted.push(value);
    },
    measureText(value: string) {
      return { width: value.length * 8 };
    },
  } as unknown as CanvasRenderingContext2D);
  vi.spyOn(HTMLCanvasElement.prototype, "toBlob").mockImplementation(
    (callback) => callback(new Blob(["png"], { type: "image/png" })),
  );
  await createReceiptPng({ ...receipt, kind: "TRANSFER", method: "FUROCASH" });
  expect(painted).toContain("Money sent");
  expect(painted).not.toContain("Deposit complete");
  expect(painted).toContain("FuroCash transfer");
  expect(painted).toContain("−₱50.00");
  expect(painted).toContain("Transfer Fee");
  expect(painted).toContain("Total Deducted");
  expect(painted).toContain("f.");
});
test("native text sharing is explicit and privacy safe; cancellation is quiet", async () => {
  let shared: ShareData | undefined;
  vi.stubGlobal("navigator", {
    share: async (data: ShareData) => {
      shared = data;
    },
  });
  expect(await shareReceipt(receipt)).toBe("shared");
  expect(shared?.text).toContain("DEMO — NOT PROOF OF PAYMENT");
  expect(shared?.text).not.toContain("FC-1234567890");
  expect(shared?.text).toContain("+₱50.00");
  expect(shared?.text).toContain("Total Credit");
  vi.stubGlobal("navigator", {
    share: async () => {
      throw new DOMException("Cancelled", "AbortError");
    },
  });
  expect(await shareReceipt(receipt)).toBe("cancelled");
});

test.each(["unavailable", "denied"])(
  "%s native sharing falls back to a local PNG download",
  async (mode) => {
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue({
      fillRect() {},
      fillText() {},
      measureText(text: string) {
        return { width: text.length * 8 };
      },
    } as unknown as CanvasRenderingContext2D);
    vi.spyOn(HTMLCanvasElement.prototype, "toBlob").mockImplementation(
      (callback) => callback(new Blob(["png"], { type: "image/png" })),
    );
    const createUrl = vi.fn(() => "blob:local-receipt");
    vi.stubGlobal("URL", { createObjectURL: createUrl, revokeObjectURL() {} });
    const clicked: string[] = [];
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      clicked.push(`${this.href}|${this.download}`);
    });
    vi.stubGlobal(
      "navigator",
      mode === "denied"
        ? {
            share: async () => {
              throw new Error("Denied");
            },
          }
        : {},
    );
    expect(await shareReceipt(receipt)).toBe("downloaded");
    expect(clicked).toEqual([
      "blob:local-receipt|furocash-demo-server-ref.png",
    ]);
    expect((createUrl.mock.calls[0] as unknown as [Blob])[0].type).toBe(
      "image/png",
    );
  },
);

test("native file sharing receives only the generated demo image", async () => {
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue({
    fillRect() {},
    fillText() {},
    measureText(text: string) {
      return { width: text.length * 8 };
    },
  } as unknown as CanvasRenderingContext2D);
  vi.spyOn(HTMLCanvasElement.prototype, "toBlob").mockImplementation(
    (callback) => callback(new Blob(["png"], { type: "image/png" })),
  );
  let shared: ShareData | undefined;
  vi.stubGlobal("navigator", {
    canShare: () => true,
    share: async (data: ShareData) => {
      shared = data;
    },
  });
  expect(await shareReceipt(receipt)).toBe("shared");
  expect(shared?.files?.[0].type).toBe("image/png");
  expect(shared?.files?.[0].size).toBe(3);
  expect(shared?.files?.[0].name).toBe("furocash-demo-server-ref.png");
  expect(shared?.url).toBeUndefined();
});
