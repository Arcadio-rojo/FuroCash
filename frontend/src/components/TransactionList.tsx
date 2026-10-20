import type { Transaction } from "../api";
import { money } from "../utils";
export default function TransactionList({
  transactions,
}: {
  transactions: Transaction[];
}) {
  if (!transactions.length)
    return (
      <div className="empty-state">
        <span aria-hidden="true">↗</span>
        <h3>Your next move starts here.</h3>
        <p>Once you send or receive money, you’ll see it here.</p>
      </div>
    );
  return (
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
  );
}
