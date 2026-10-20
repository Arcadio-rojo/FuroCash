import { money } from "../utils";

export default function BalanceCard({
  balance,
  hidden,
  onToggle,
}: {
  balance: number;
  hidden: boolean;
  onToggle: () => void;
}) {
  return (
    <section className="balance-card">
      <div className="balance-top">
        <span>Available balance</span>
        <button
          type="button"
          onClick={onToggle}
          aria-label={hidden ? "Show balance" : "Hide balance"}
        >
          {hidden ? "Show" : "Hide"}
        </button>
      </div>
      <p className="balance-amount">{hidden ? "₱ ••••••" : money(balance)}</p>
      <div className="balance-bottom">
        <span>PHP · Philippine Peso</span>
        <span className="card-logo">f.</span>
      </div>
    </section>
  );
}
