import { useState } from "react";
import { api } from "../api";
import type { Receipt, Recipient, ResolvedQr, TransferReview } from "../api";
import { useAuth } from "../context/session";
import FormField from "../components/FormField";
import {
  PageTitle,
  DemoNotice,
  ErrorNotice,
  validAmount,
  amountError,
  errorMessage,
} from "../components/BankingUi";
import { Spinner } from "../components/LoadingState";
import TransactionReceipt from "../components/TransactionReceipt";
import { maskReceiptAccount } from "../receiptExport";
import { money } from "../utils";
import { MoneyRecovery, useMoneyOperation } from "./useMoneyOperation";
import "./moneyFlows.css";

export default function Transfer({
  initial,
  onComplete,
  balance,
}: {
  initial: ResolvedQr | null;
  onComplete: (balance: number) => void;
  balance?: number;
}) {
  const { user } = useAuth();
  const [identifier, setIdentifier] = useState(
    initial?.recipient.accountIdentifier ?? "",
  );
  const [recipient, setRecipient] = useState<Recipient | null>(
    initial?.recipient ?? null,
  );
  const [amount, setAmount] = useState(initial?.amount ?? "");
  const [note, setNote] = useState("");
  const [review, setReview] = useState<TransferReview | null>(null);
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [finding, setFinding] = useState(false);
  const [reviewing, setReviewing] = useState(false);
  const [error, setError] = useState("");
  const operation = useMoneyOperation((value) => {
    setReceipt(value);
    setReview(null);
    onComplete(value.balance);
  });
  const quote =
    operation.attempt?.kind === "TRANSFER" ? operation.attempt.preview : review;
  async function lookup() {
    if (finding) return;
    setFinding(true);
    setError("");
    try {
      setRecipient(await api.recipient(identifier.trim().toUpperCase()));
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setFinding(false);
    }
  }
  async function prepare() {
    if (reviewing || !recipient) return;
    if (!validAmount(amount)) {
      setError(amountError);
      return;
    }
    setReviewing(true);
    setError("");
    try {
      const value = await api.reviewTransfer({
        receiverAccount: recipient.accountIdentifier,
        amount,
        note: note.trim(),
        ...(initial ? { qrReference: initial.reference } : {}),
      });
      if (
        !value.reference ||
        !value.recipient ||
        !Number.isFinite(value.fee) ||
        !Number.isFinite(value.total)
      )
        throw new Error(
          "Complete transfer review details were not returned. Please try again.",
        );
      operation.resetReview();
      setRecipient(value.recipient);
      setReview(value);
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setReviewing(false);
    }
  }
  function another() {
    setReceipt(null);
    setReview(null);
    setRecipient(null);
    setIdentifier("");
    setAmount("");
    setNote("");
    setError("");
    operation.resetReview();
  }
  if (receipt)
    return (
      <TransactionReceipt
        receipt={receipt}
        title="Transfer Successful"
        onAnother={another}
        anotherLabel="Make Another Transfer"
      />
    );
  return (
    <>
      <PageTitle
        eyebrow="MAKE SOMEONE’S DAY"
        title={quote ? "Review Transfer" : "Transfer"}
      >
        {quote
          ? "Please check the details before sending."
          : "Send to another FuroCash account."}
      </PageTitle>
      <section className="bank-panel form-panel transfer-review">
        {operation.attempt?.kind === "MANUAL_DEPOSIT" ? (
          <p className="notice">
            Resolve your saved deposit before starting another transaction.{" "}
            <a href="#/deposit">Continue deposit recovery</a>
          </p>
        ) : quote ? (
          <>
            <p className="review-label">You’re sending</p>
            <p className="review-amount">{money(quote.amount)}</p>
            <dl className="detail-list">
              <div>
                <dt>Send To</dt>
                <dd>{quote.recipient.fullName}</dd>
              </div>
              <div>
                <dt>Account</dt>
                <dd>{maskReceiptAccount(quote.recipient.accountIdentifier)}</dd>
              </div>
              <div>
                <dt>Transfer Amount</dt>
                <dd>{money(quote.amount)}</dd>
              </div>
              <div>
                <dt>Transfer Fee</dt>
                <dd>{money(quote.fee)}</dd>
              </div>
              <div>
                <dt>Total Deduction</dt>
                <dd>{money(quote.total)}</dd>
              </div>
              {quote.note && (
                <div>
                  <dt>Message</dt>
                  <dd>{quote.note}</dd>
                </div>
              )}
              <div>
                <dt>Transfer Type</dt>
                <dd>FuroCash Wallet Transfer (Demo)</dd>
              </div>
              <div>
                <dt>Available Balance</dt>
                <dd>{money(quote.balance)}</dd>
              </div>
            </dl>
            <p className="transfer-summary-notice">
              Demo transfer — Simulated funds only. Recipient, fees, available
              balance, and QR requests are checked again when you confirm.
            </p>
            <ErrorNotice error={operation.error || error} />
            <MoneyRecovery operation={operation} />
            {!operation.attempt && (
              <div className="button-row">
                <button
                  className="secondary"
                  disabled={operation.busy}
                  onClick={() => {
                    setReview(null);
                    operation.resetReview();
                    setError("");
                  }}
                >
                  Back to Edit
                </button>
                {operation.needsReview ? (
                  <button
                    className="btn"
                    onClick={() => {
                      setReview(null);
                      operation.resetReview();
                    }}
                  >
                    Review details again
                  </button>
                ) : (
                  <button
                    className="btn"
                    disabled={operation.busy || operation.blocked}
                    aria-busy={operation.busy}
                    onClick={() =>
                      void operation.confirm({
                        kind: "TRANSFER",
                        preview: quote,
                        payload: {
                          receiverAccount: quote.recipient.accountIdentifier,
                          amount: quote.amount.toFixed(2),
                          note: quote.note,
                          reviewReference: quote.reference,
                          ...(quote.qrReference
                            ? { qrReference: quote.qrReference }
                            : {}),
                        },
                      })
                    }
                  >
                    {operation.busy && <Spinner />}Confirm Transfer
                  </button>
                )}
              </div>
            )}
          </>
        ) : (
          <>
            <p className="available-wallet-balance">
              Available balance{" "}
              <strong>{money(balance ?? user!.balance)}</strong>
            </p>
            <form
              onSubmit={(event) => {
                event.preventDefault();
                void lookup();
              }}
            >
              <fieldset disabled={finding || !!initial}>
                <FormField
                  id="recipient"
                  label="Account identifier or mobile"
                  value={identifier}
                  onChange={(value) => {
                    setIdentifier(value);
                    setRecipient(null);
                  }}
                  placeholder="FC-0000000000 or 09…"
                  maxLength={32}
                  required
                />
                <button
                  className="secondary"
                  disabled={finding || !identifier.trim()}
                  aria-busy={finding}
                >
                  {finding && <Spinner />}
                  {finding ? "Finding…" : "Find recipient"}
                </button>
              </fieldset>
            </form>
            <ErrorNotice error={error} />
            {recipient && (
              <>
                <div className="recipient-found">
                  <span className="avatar">{recipient.fullName[0]}</span>
                  <div>
                    <strong>{recipient.fullName}</strong>
                    <small>
                      {maskReceiptAccount(recipient.accountIdentifier)}
                    </small>
                  </div>
                  <span className="badge">VERIFIED</span>
                </div>
                <form
                  onSubmit={(event) => {
                    event.preventDefault();
                    void prepare();
                  }}
                >
                  <fieldset disabled={reviewing || operation.blocked}>
                    <FormField
                      id="transfer-amount"
                      label="Amount (PHP)"
                      value={amount}
                      onChange={setAmount}
                      inputMode="decimal"
                      placeholder="0.00"
                      maxLength={13}
                      required
                      readOnly={initial?.amount != null}
                    />
                    {initial?.amount != null && (
                      <p className="subtle">
                        The amount is fixed by this QR request.
                      </p>
                    )}
                    <FormField
                      id="transfer-note"
                      label="Note (optional)"
                      value={note}
                      onChange={setNote}
                      maxLength={140}
                      placeholder="What’s it for?"
                    />
                    <ErrorNotice error={operation.error} />
                    <button
                      className="btn"
                      disabled={!validAmount(amount) || reviewing}
                      aria-busy={reviewing}
                    >
                      {reviewing && <Spinner />}
                      {reviewing ? "Checking transfer…" : "Review Transfer"}
                    </button>
                  </fieldset>
                </form>
              </>
            )}
          </>
        )}
        {operation.blocked && <ErrorNotice error={operation.error} />}
        <DemoNotice />
      </section>
    </>
  );
}
