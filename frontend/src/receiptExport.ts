import type { Receipt } from "./api";
import { money } from "./utils";

export const receiptWatermark = "DEMO — NOT PROOF OF PAYMENT";
function isOutgoingReceipt(receipt: Receipt) {
  return ["TRANSFER", "TRANSFER_OUT", "CARD_PURCHASE"].includes(receipt.kind);
}
export function signedReceiptAmount(receipt: Receipt) {
  return `${isOutgoingReceipt(receipt) ? "−" : "+"}${money(receipt.amount)}`;
}
export function maskReceiptAccount(account: string) {
  return account.length > 4 ? `•••• ${account.slice(-4)}` : "••••";
}
export function receiptText(receipt: Receipt, value: string) {
  let result = value;
  for (const account of [
    receipt.accountIdentifier,
    receipt.senderAccount,
    receipt.recipientAccount,
  ]) {
    if (account)
      result = result.split(account).join(maskReceiptAccount(account));
  }
  return result.replace(/FC-[A-Za-z0-9]+/g, (account) =>
    maskReceiptAccount(account),
  );
}
export function receiptTitle(receipt: Receipt) {
  if (receipt.kind === "TRANSFER" || receipt.kind === "TRANSFER_OUT")
    return "Money sent";
  if (receipt.kind === "TRANSFER_IN") return "Money received";
  if (receipt.kind === "CARD_PURCHASE") return "Purchase complete";
  return "Deposit complete";
}
export function receiptDetails(receipt: Receipt): [string, string][] {
  const rows: [string, string][] = [
    ["Status", receipt.status],
    ["Reference", receipt.reference],
    ["Account", maskReceiptAccount(receipt.accountIdentifier)],
  ];
  if (receipt.method)
    rows.push([
      "Method",
      receipt.method === "FUROCASH"
        ? "FuroCash transfer"
        : receipt.method.replace(/_/g, " "),
    ]);
  if (receipt.senderName) rows.push(["Sender", receipt.senderName]);
  if (receipt.senderAccount)
    rows.push(["Sender account", maskReceiptAccount(receipt.senderAccount)]);
  if (receipt.recipientName) rows.push(["Recipient", receipt.recipientName]);
  if (receipt.recipientAccount)
    rows.push([
      "Recipient account",
      maskReceiptAccount(receipt.recipientAccount),
    ]);
  if (receipt.counterparty && !receipt.recipientName)
    rows.push(["With", receipt.counterparty]);
  if (receipt.fee != null)
    rows.push([
      ["TRANSFER", "TRANSFER_OUT", "TRANSFER_IN"].includes(receipt.kind)
        ? "Transfer Fee"
        : "Processing Fee",
      money(receipt.fee),
    ]);
  if (receipt.total != null)
    rows.push([
      isOutgoingReceipt(receipt) ? "Total Deducted" : "Total Credit",
      money(receipt.total),
    ]);
  if (receipt.note) rows.push(["Note", receipt.note]);
  rows.push(
    ["Date", new Date(receipt.createdAt).toLocaleString("en-PH")],
    ["Available balance", money(receipt.balance)],
  );
  return rows.map(([label, value]) => [label, receiptText(receipt, value)]);
}
function requireCompleted(receipt: Receipt) {
  if (receipt.status !== "COMPLETED")
    throw new Error(
      "This transaction is not completed. A receipt is not available.",
    );
}
export async function createReceiptPng(
  receipt: Receipt,
  title = receiptTitle(receipt),
): Promise<Blob> {
  requireCompleted(receipt);
  const canvas = document.createElement("canvas");
  const context = canvas.getContext("2d");
  if (!context) throw new Error("PNG export is unavailable in this browser.");
  const width = 800;
  // Wrap by measured glyph width so long notes/references remain fully visible.
  context.font = "20px sans-serif";
  const wrap = (value: string) => {
    const lines: string[] = [];
    let line = "";
    for (const char of value) {
      if (
        char === "\n" ||
        context.measureText(line + char).width > width - 96
      ) {
        lines.push(line);
        line = char === "\n" ? "" : char;
      } else line += char;
    }
    lines.push(line);
    return lines;
  };
  const details = receiptDetails(receipt).map(([label, value]) => ({
    label,
    lines: wrap(value),
  }));
  const heading = wrap(receiptText(receipt, title));
  canvas.width = width;
  canvas.height =
    330 +
    heading.length * 30 +
    details.reduce((height, row) => height + 40 + row.lines.length * 28, 0);
  context.fillStyle = "#f5f8f4";
  context.fillRect(0, 0, width, canvas.height);
  context.fillStyle = "#18513e";
  context.font = "bold 26px sans-serif";
  context.fillText("f.", 48, 60);
  context.fillText("FuroCash", 90, 60);
  context.font = "bold 19px sans-serif";
  context.fillText(receiptWatermark, 48, 103);
  context.font = "bold 26px sans-serif";
  let y = 158;
  for (const line of heading) {
    context.fillText(line, 48, y);
    y += 30;
  }
  context.font = "bold 38px sans-serif";
  context.fillText(signedReceiptAmount(receipt), 48, y + 30);
  y += 90;
  for (const { label, lines } of details) {
    context.fillStyle = "#56645c";
    context.font = "17px sans-serif";
    context.fillText(label, 48, y);
    y += 28;
    context.fillStyle = "#17392c";
    context.font = "20px sans-serif";
    for (const line of lines) {
      context.fillText(line, 48, y);
      y += 28;
    }
    y += 12;
  }
  context.fillStyle = "#18513e";
  context.font = "bold 19px sans-serif";
  context.fillText(receiptWatermark, 48, canvas.height - 40);
  return new Promise((resolve, reject) =>
    canvas.toBlob(
      (blob) =>
        blob
          ? resolve(blob)
          : reject(new Error("Could not create the receipt image.")),
      "image/png",
    ),
  );
}
function receiptFilename(receipt: Receipt) {
  return `furocash-demo-${receipt.reference.replace(/[^a-zA-Z0-9-]/g, "_")}.png`;
}
async function saveReceiptPng(receipt: Receipt, title?: string) {
  const blob = await createReceiptPng(receipt, title);
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = receiptFilename(receipt);
  document.body.append(link);
  try {
    link.click();
  } finally {
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
}
export const downloadReceipt = saveReceiptPng;
export async function shareReceipt(
  receipt: Receipt,
  title = receiptTitle(receipt),
): Promise<"shared" | "downloaded" | "cancelled"> {
  requireCompleted(receipt);
  if (typeof navigator.share === "function") {
    try {
      const safeTitle = receiptText(receipt, title);
      if (
        typeof navigator.canShare === "function" &&
        navigator.canShare({
          files: [new File([], "receipt.png", { type: "image/png" })],
        })
      ) {
        const blob = await createReceiptPng(receipt, safeTitle);
        await navigator.share({
          title: `FuroCash · ${safeTitle}`,
          files: [
            new File([blob], receiptFilename(receipt), { type: "image/png" }),
          ],
        });
      } else {
        await navigator.share({
          title: `FuroCash · ${safeTitle}`,
          text: [
            receiptWatermark,
            safeTitle,
            signedReceiptAmount(receipt),
            ...receiptDetails(receipt).map(
              ([key, value]) => `${key}: ${value}`,
            ),
          ].join("\n"),
        });
      }
      return "shared";
    } catch (error) {
      if (error instanceof Error && error.name === "AbortError")
        return "cancelled";
      // A denied or unsupported native share falls back to a local download.
    }
  }
  await saveReceiptPng(receipt, title);
  return "downloaded";
}
