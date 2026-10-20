import type { Transaction } from "../api";
import { useState } from "react";
import { api, type Receipt } from "../api";
import { money } from "../utils";
import TransactionReceipt from "./TransactionReceipt";
export default function TransactionList({
  transactions,
}: {
  transactions: Transaction[];
}) {
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [loading, setLoading] = useState<string | null>(null);
  const [error, setError] = useState("");
  async function viewReceipt(reference: string) {
    setLoading(reference);
    setError("");
    try {
      const result = await api.transactionReceipt(reference);
      if (result.status !== "COMPLETED")
        throw new Error(
          "This transaction is not completed. A receipt is not available.",
        );
      setReceipt(result);
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Could not load the receipt. Please try again.",
      );
    } finally {
      setLoading(null);
    }
  }
  if (receipt)
    return (
      <TransactionReceipt receipt={receipt} onBack={() => setReceipt(null)} />
    );
  if (!transactions.length)
    return (
      <div className="empty-state">
        <span aria-hidden="true">↗</span>
        <h3>Your next move starts here.</h3>
        <p>Once you send or receive money, you’ll see it here.</p>
      </div>
    );
  return (
    <>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <ul className="transaction-list">
        {transactions.map((tx) => {
          const outgoing =
            tx.type === "TRANSFER_OUT" || tx.type === "CARD_PURCHASE";
          return (
            <li key={tx.id}>
              <span
                className={`tx-icon ${outgoing ? "out" : "in"}`}
                aria-hidden="true"
              >
                {outgoing ? "↗" : "↙"}
              </span>
              <div className="tx-info">
                <strong>
                  {tx.type === "CASH_IN"
                    ? "Demo cash-in"
                    : tx.type === "CARD_PURCHASE"
                      ? "Card purchase"
                      : outgoing
                        ? "Money sent"
                        : "Money received"}
                </strong>
                <span>{tx.details}</span>
                <small>
                  {new Date(tx.createdAt).toLocaleString("en-PH", {
                    dateStyle: "medium",
                    timeStyle: "short",
                  })}{" "}
                  · {tx.referenceNo}
                </small>
                {tx.receiptAvailable && tx.status === "COMPLETED" && (
                  <button
                    className="tx-receipt-button"
                    disabled={loading !== null}
                    onClick={() => void viewReceipt(tx.referenceNo)}
                  >
                    {loading === tx.referenceNo
                      ? "Loading receipt…"
                      : "View receipt"}
                  </button>
                )}
              </div>
              <div className={`tx-amount ${outgoing ? "" : "positive"}`}>
                <strong>
                  {outgoing ? "−" : "+"}
                  {money(tx.amount)}
                </strong>
                <small>Balance {money(tx.balanceAfter)}</small>
              </div>
            </li>
          );
        })}
      </ul>
    </>
  );
}
