import { expect, test } from "vitest";
import QRCode from "qrcode";
import { parseQr, encodeQr } from "../src/qr";
const payload = {
  app: "FUROCASH" as const,
  version: 1 as const,
  purpose: "TRANSFER" as const,
  accountIdentifier: "FC-0000000001",
  reference: "12345678-1234-4234-8234-123456789abc",
  amount: "25.50",
};
test("QR codec round trips only the versioned FuroCash schema", () => {
  expect(parseQr(encodeQr(payload))).toEqual(payload);
  for (const bad of [
    { ...payload, app: "OTHER" },
    { ...payload, version: 2 },
    { ...payload, amount: 25 },
    { ...payload, amount: "0" },
    { ...payload, reference: "bad" },
    { ...payload, secret: "no" },
  ])
    expect(() => parseQr(JSON.stringify(bad))).toThrow();
  expect(() => parseQr("https://example.com/pay")).toThrow();
});
test("payload generates a real QR image", async () => {
  expect(await QRCode.toDataURL(encodeQr(payload))).toMatch(
    /^data:image\/png;base64,/,
  );
});
