import { useState } from "react";
import type { Receipt } from "../api";
import {
  downloadReceipt,
  receiptDetails,
  receiptText,
  receiptTitle,
  receiptWatermark,
  shareReceipt,
  signedReceiptAmount,
} from "../receiptExport";
import "./TransactionReceipt.css";

export interface TransactionReceiptProps {
  receipt: Receipt;
  title?: string;
  onAnother?: () => void;
  anotherLabel?: string;
  onBack?: () => void;
}
export default function TransactionReceipt({
  receipt,
  title = receiptTitle(receipt),
  onAnother,
  anotherLabel = "Make another transaction",
  onBack,
}: TransactionReceiptProps) {
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  if (receipt.status !== "COMPLETED")
    return (
      <section className="bank-panel transaction-receipt">
        <p role="status">
          This transaction is not completed. A success receipt is not available.
        </p>
        {onBack ? (
          <button className="btn secondary" onClick={onBack}>
            Back to transaction history
          </button>
        ) : (
          <a className="btn secondary" href="#/transactions">
            View transaction history
          </a>
        )}
      </section>
    );
  async function exportReceipt(share: boolean) {
    setBusy(true);
    setMessage("");
    try {
      if (share) {
        const outcome = await shareReceipt(receipt, title);
        setMessage(
          outcome === "downloaded"
            ? "Native sharing is unavailable. Your receipt image was downloaded."
            : outcome === "cancelled"
              ? "Sharing cancelled."
              : "Receipt shared.",
        );
      } else {
        await downloadReceipt(receipt, title);
        setMessage("Receipt image downloaded.");
      }
    } catch (error) {
      setMessage(
        error instanceof Error
          ? error.message
          : "Could not export the receipt. Please try again.",
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <section
      className="bank-panel receipt transaction-receipt"
      aria-label="Transaction receipt"
    >
      <div className="transaction-receipt-brand">
        <strong>
          <span className="brand-mark" aria-hidden="true">
            f.
          </span>{" "}
          FuroCash
        </strong>{" "}
        <span className="transaction-receipt-demo-label">Demo wallet</span>
      </div>
      <span className="receipt-check" aria-hidden="true">
        ✓
      </span>
      <span className="eyebrow">COMPLETED</span>
      <h1>{receiptText(receipt, title)}</h1>
      <p className="receipt-amount">{signedReceiptAmount(receipt)}</p>
      <p className="transaction-receipt-watermark">{receiptWatermark}</p>
      <dl className="detail-list">
        {receiptDetails(receipt).map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
      <div className="transaction-receipt-actions">
        <button
          className="btn"
          disabled={busy}
          onClick={() => void exportReceipt(false)}
        >
          Download Receipt
        </button>
        <button
          className="btn secondary"
          disabled={busy}
          onClick={() => void exportReceipt(true)}
        >
          Share Receipt
        </button>
        {onAnother && (
          <button className="btn secondary" onClick={onAnother}>
            {anotherLabel}
          </button>
        )}
      </div>
      <p role="status" className="transaction-receipt-message">
        {busy
          ? "Preparing receipt…"
          : message || "Transaction completed successfully."}
      </p>
      <div className="transaction-receipt-navigation">
        <a href="#/home">Back to dashboard</a>
        {onBack ? (
          <button onClick={onBack}>Back to transaction history</button>
        ) : (
          <a href="#/transactions">View transaction history</a>
        )}
      </div>
    </section>
  );
}
