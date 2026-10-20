import { useCallback, useEffect, useState } from "react";
import { api } from "../api";
import type { DemoCard, Receipt, Transaction } from "../api";
import { money } from "../utils";
import ConfirmDialog from "../components/ConfirmDialog";
import FormField from "../components/FormField";
import TransactionList from "../components/TransactionList";
import {
  PageTitle,
  DemoNotice,
  ErrorNotice,
  PaymentReceipt,
  errorMessage,
  validAmount,
  amountError,
} from "../components/BankingUi";
import { useOperation } from "./useOperation";
import LoadingState from "../components/LoadingState";
type Action = "create" | "freeze" | "unfreeze" | "limit" | "purchase";
export default function CardPage({
  balance,
  onComplete,
}: {
  balance: number;
  onComplete: (balance: number) => void;
}) {
  const [card, setCard] = useState<DemoCard | null>(null);
  const [loading, setLoading] = useState(true);
  const [history, setHistory] = useState<Transaction[]>([]);
  const [error, setError] = useState("");
  const [revealed, setRevealed] = useState(false);
  const [limit, setLimit] = useState("");
  const [amount, setAmount] = useState("");
  const [merchant, setMerchant] = useState("");
  const [confirm, setConfirm] = useState<Action | null>(null);
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const op = useOperation();
  const load = useCallback(async () => {
    try {
      const value = await api.card();
      setCard(value);
      if (value) {
        setLimit(String(value.dailyLimit));
        setHistory(await api.cardTransactions(value.id));
      }
      setError("");
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => {
    void load();
  }, [load]);
  async function apply() {
    if (!confirm) return;
    const action = confirm;
    if (action === "purchase" && card) {
      void op.run(
        { cardId: card.id, amount, merchant: merchant.trim() },
        (key) => api.purchase(card.id, amount, merchant.trim(), key),
        (value) => {
          setReceipt(value);
          onComplete(value.balance);
          setConfirm(null);
          void load();
        },
      );
      return;
    }
    void op.run(
      { action, id: card?.id, limit: action === "limit" ? limit : undefined },
      async () =>
        action === "create"
          ? api.createCard()
          : action === "limit"
            ? api.cardLimit(card!.id, limit)
            : api.cardStatus(card!.id, action as "freeze" | "unfreeze"),
      (value) => {
        setCard(value);
        setLimit(String(value.dailyLimit));
        setConfirm(null);
      },
    );
  }
  if (receipt)
    return (
      <>
        <PaymentReceipt receipt={receipt} title="Demo purchase complete" />
        <button
          className="secondary back-button"
          onClick={() => {
            setReceipt(null);
            setAmount("");
            setMerchant("");
          }}
        >
          Back to card
        </button>
      </>
    );
  return (
    <>
      <PageTitle eyebrow="A LITTLE POSSIBILITY" title="Your demo card">
        Explore a virtual card, with your FuroCash balance.
      </PageTitle>
      <ErrorNotice error={error} />
      {loading ? (
        <LoadingState label="Getting your card…" rows={2} />
      ) : error && !card ? (
        <button className="secondary" onClick={() => void load()}>
          Retry card
        </button>
      ) : !card ? (
        <section className="bank-panel form-panel">
          <DemoNotice />
          <h2>Meet your FuroCash demo card</h2>
          <p>
            An internal demo identifier for simulated purchases. It cannot be
            used at real merchants.
          </p>
          <button className="btn" onClick={() => setConfirm("create")}>
            Create demo card
          </button>
        </section>
      ) : (
        <div className="payment-grid">
          <div>
            <section
              className={`demo-card ${card.status === "FROZEN" ? "frozen" : ""}`}
            >
              <div className="card-brand">
                <strong>
                  <span className="brand-mark">f.</span> FuroCash
                </strong>
                <span className="badge">{card.status}</span>
              </div>
              <span className="card-caption">
                VIRTUAL DEBIT · INTERNAL DEMO CARD
              </span>
              <p className="card-identifier">
                {revealed ? card.identifier : card.maskedIdentifier}
              </p>
              <button
                className="card-reveal"
                onClick={() => setRevealed(!revealed)}
              >
                {revealed ? "Hide identifier" : "Reveal identifier"}
              </button>
              <div className="card-bottom">
                <strong>{card.cardholderName}</strong>
                <span>
                  DEMO EXPIRY
                  <br />
                  {card.expiresOn}
                </span>
              </div>
            </section>
            <DemoNotice />
            <section className="bank-panel">
              <h2>Card controls</h2>
              <dl className="detail-list">
                <div>
                  <dt>Available account balance</dt>
                  <dd>{money(balance)}</dd>
                </div>
                <div>
                  <dt>Spent today</dt>
                  <dd>{money(card.spentToday)}</dd>
                </div>
                <div>
                  <dt>Daily limit</dt>
                  <dd>{money(card.dailyLimit)}</dd>
                </div>
              </dl>
              <button
                className="secondary"
                onClick={() =>
                  setConfirm(card.status === "ACTIVE" ? "freeze" : "unfreeze")
                }
              >
                {card.status === "ACTIVE" ? "Freeze card" : "Unfreeze card"}
              </button>
              <form
                className="spaced-form"
                onSubmit={(e) => {
                  e.preventDefault();
                  if (!validAmount(limit)) {
                    op.setError(amountError);
                    return;
                  }
                  setConfirm("limit");
                }}
              >
                <FormField
                  id="daily-limit"
                  label="Daily spending limit (PHP)"
                  value={limit}
                  onChange={setLimit}
                  inputMode="decimal"
                  maxLength={13}
                  required
                />
                <button className="secondary">Review limit change</button>
              </form>
            </section>
          </div>
          <div>
            <section className="bank-panel">
              <h2>Try a demo purchase</h2>
              <p>
                Uses your account balance and daily spending limit. No real
                merchant is charged.
              </p>
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  if (!validAmount(amount)) {
                    op.setError(amountError);
                    return;
                  }
                  if (!merchant.trim()) {
                    op.setError("Enter a demo merchant name.");
                    return;
                  }
                  setConfirm("purchase");
                }}
              >
                <fieldset disabled={card.status !== "ACTIVE"}>
                  <FormField
                    id="purchase-merchant"
                    label="Demo merchant"
                    value={merchant}
                    onChange={setMerchant}
                    maxLength={100}
                    placeholder="Everyday Coffee"
                    required
                  />
                  <FormField
                    id="purchase-amount"
                    label="Purchase amount (PHP)"
                    value={amount}
                    onChange={setAmount}
                    inputMode="decimal"
                    maxLength={13}
                    required
                  />
                  <button className="btn">Review demo purchase</button>
                </fieldset>
              </form>
              {card.status === "FROZEN" && (
                <p className="notice">
                  Unfreeze your card to simulate a purchase.
                </p>
              )}
            </section>
            <section className="bank-panel card-history">
              <h2>Card activity</h2>
              <TransactionList transactions={history} />
            </section>
          </div>
        </div>
      )}
      <ErrorNotice error={confirm ? "" : op.error} />
      {confirm && (
        <ConfirmDialog
          busy={op.busy}
          onClose={() => {
            setConfirm(null);
            op.setError("");
          }}
        >
          <h2 id="card-confirm">
            {confirm === "create"
              ? "Create demo card?"
              : confirm === "freeze"
                ? "Freeze this card?"
                : confirm === "unfreeze"
                  ? "Unfreeze this card?"
                  : confirm === "limit"
                    ? "Change daily limit?"
                    : "Confirm demo purchase"}
          </h2>
          <p>
            {confirm === "create"
              ? "Create one internal FuroCash demo card for this account."
              : confirm === "limit"
                ? `Set your daily limit to ${money(Number(limit))}.`
                : confirm === "purchase"
                  ? `Pay ${money(Number(amount))} to ${merchant.trim()} from your FuroCash balance.`
                  : confirm === "freeze"
                    ? "Simulated purchases will be blocked until you unfreeze."
                    : "Simulated purchases will be available again."}
          </p>
          <DemoNotice />
          <ErrorNotice error={op.error} />
          <div className="button-row">
            <button
              className="btn"
              disabled={op.busy}
              onClick={() => void apply()}
            >
              {op.busy ? "Working…" : "Confirm"}
            </button>
            <button
              className="secondary"
              disabled={op.busy}
              onClick={() => {
                setConfirm(null);
                op.setError("");
              }}
            >
              Cancel
            </button>
          </div>
        </ConfirmDialog>
      )}
    </>
  );
}
