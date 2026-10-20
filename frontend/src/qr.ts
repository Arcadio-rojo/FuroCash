import type { QrPayload } from "./api";
export function parseQr(text: string): QrPayload {
  const fail = () => {
    throw new Error("This is not a valid FuroCash demo QR code.");
  };
  let value: unknown;
  try {
    value = JSON.parse(text);
  } catch {
    return fail();
  }
  if (!value || typeof value !== "object" || Array.isArray(value))
    return fail();
  const p = value as Record<string, unknown>;
  if (
    Object.keys(p).sort().join() !==
      "accountIdentifier,amount,app,purpose,reference,version" ||
    p.app !== "FUROCASH" ||
    p.version !== 1 ||
    !["TRANSFER", "DEPOSIT"].includes(String(p.purpose)) ||
    typeof p.accountIdentifier !== "string" ||
    !/^FC-\d{10,}$/.test(p.accountIdentifier) ||
    typeof p.reference !== "string" ||
    !/^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i.test(
      p.reference,
    ) ||
    !(
      p.amount === null ||
      (typeof p.amount === "string" &&
        /^\d+(\.\d{1,2})?$/.test(p.amount) &&
        Number(p.amount) > 0 &&
        Number(p.amount) <= 999999999)
    )
  )
    return fail();
  return p as unknown as QrPayload;
}
export const encodeQr = (payload: QrPayload) =>
  JSON.stringify(parseQr(JSON.stringify(payload)));
