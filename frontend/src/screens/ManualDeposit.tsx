import { useEffect, useState } from "react";
import { api } from "../api";
import type { DepositOptions, Receipt } from "../api";
import FormField from "../components/FormField";
import {
  ErrorNotice,
  errorMessage,
  validAmount,
} from "../components/BankingUi";
import LoadingState, { Spinner } from "../components/LoadingState";
import TransactionReceipt from "../components/TransactionReceipt";
import { maskReceiptAccount } from "../receiptExport";
import { money } from "../utils";
import { useOperation } from "./useOperation";
import { MoneyRecovery, useMoneyOperation } from "./useMoneyOperation";
import "./moneyFlows.css";

export default function ManualDeposit({
  onComplete,
}: {
  onComplete: (balance: number) => void;
}) {
  const [options, setOptions] = useState<DepositOptions | null>(null);
  const [loadError, setLoadError] = useState("");
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [amount, setAmount] = useState("");
  const [method, setMethod] = useState("");
  const [note, setNote] = useState("");
  const [review, setReview] = useState<Receipt | null>(null);
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const prepare = useOperation();
  const operation = useMoneyOperation((value) => {
    setReceipt(value);
    setReview(null);
    onComplete(value.balance);
  });
  useEffect(() => {
    let active = true;
    api
      .depositsOptions()
      .then((value) => {
        if (active) {
          setOptions(value);
          setLoadError("");
        }
      })
      .catch((error) => {
        if (active) setLoadError(errorMessage(error));
      });
    return () => {
      active = false;
    };
  }, [loadAttempt]);
  const valid =
    !!options &&
    validAmount(amount) &&
    Number(amount) >= options.minAmount &&
    Number(amount) <= options.manualMaxAmount &&
    options.methods.some((value) => value.value === method) &&
    note.length <= 150 &&
    !/[\u0000-\u001f\u007f]/.test(note);
  const pending =
    operation.attempt?.kind === "MANUAL_DEPOSIT"
      ? operation.attempt.preview
      : review;
  const other = operation.attempt?.kind === "TRANSFER";
  function another() {
    setReceipt(null);
    setReview(null);
    setAmount("");
    setMethod("");
    setNote("");
    operation.resetReview();
    prepare.setError("");
  }
  return (
    <section
      className="bank-panel manual-deposit-section"
      role="region"
      aria-labelledby="manual-deposit-heading"
    >
      <div className="manual-section-heading">
        <div>
          <h2 id="manual-deposit-heading">Manual Deposit</h2>
          <p>Add funds manually to your FuroCash demo wallet.</p>
        </div>
        <span className="badge">DEMO ONLY</span>
      </div>
      <p className="demo-disclaimer">
        Demo transaction — No real money involved.
      </p>
      {receipt ? (
        <TransactionReceipt
          receipt={receipt}
          title="Deposit Successful"
          onAnother={another}
          anotherLabel="Make Another Deposit"
        />
      ) : other ? (
        <p className="notice">
          Resolve your saved transfer before starting another transaction.{" "}
          <a href="#/transfer">Continue transfer recovery</a>
        </p>
      ) : !options ? (
        loadError ? (
          <>
            <ErrorNotice error={loadError} />
            <button
              className="secondary"
              onClick={() => setLoadAttempt((value) => value + 1)}
            >
              Retry deposit settings
            </button>
          </>
        ) : (
          <LoadingState label="Loading manual deposit settings…" rows={2} />
        )
      ) : (
        <>
          {!options.enabled && (
            <p className="notice">
              Manual deposits are disabled because demo funding is not enabled
              on the server.
            </p>
          )}
          {pending ? (
            <div className="manual-review">
              <h3>Review Deposit</h3>
              <p className="review-amount">{money(pending.amount)}</p>
              <dl className="detail-list">
                <div>
                  <dt>Deposit To</dt>
                  <dd>
                    {pending.recipientName}
                    <br />
                    {maskReceiptAccount(
                      pending.recipientAccount ?? pending.accountIdentifier,
                    )}
                  </dd>
                </div>
                <div>
                  <dt>Deposit Amount</dt>
                  <dd>{money(pending.amount)}</dd>
                </div>
                <div>
                  <dt>Method</dt>
                  <dd>{pending.method ?? pending.counterparty}</dd>
                </div>
                {pending.fee != null && (
                  <div>
                    <dt>Processing Fee</dt>
                    <dd>{money(pending.fee)}</dd>
                  </div>
                )}
                {pending.total != null && (
                  <div>
                    <dt>Total Credit</dt>
                    <dd>{money(pending.total)}</dd>
                  </div>
                )}
                {pending.note && (
                  <div>
                    <dt>Notes</dt>
                    <dd>{pending.note}</dd>
                  </div>
                )}
              </dl>
              <ErrorNotice error={operation.error} />
              <MoneyRecovery
                operation={operation}
                allowRetry={options.enabled}
              />
              {!operation.attempt && (
                <div className="button-row">
                  <button
                    className="secondary"
                    disabled={operation.busy}
                    onClick={() => {
                      setReview(null);
                      operation.resetReview();
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
                      disabled={
                        !options.enabled ||
                        operation.busy ||
                        operation.blocked ||
                        pending.fee == null ||
                        pending.total == null
                      }
                      aria-busy={operation.busy}
                      onClick={() =>
                        void operation.confirm({
                          kind: "MANUAL_DEPOSIT",
                          payload: { reference: pending.reference },
                          preview: pending,
                        })
                      }
                    >
                      {operation.busy && <Spinner />}Confirm Demo Deposit
                    </button>
                  )}
                </div>
              )}
            </div>
          ) : (
            <form
              onSubmit={(event) => {
                event.preventDefault();
                if (!valid || !options.enabled || prepare.busy) return;
                void prepare.run(
                  { amount, method, note: note.trim() },
                  (key) =>
                    api.prepareManualDeposit(
                      { amount, method, note: note.trim() },
                      key,
                    ),
                  (value) => {
                    operation.resetReview();
                    setReview(value);
                  },
                );
              }}
            >
              <fieldset
                disabled={!options.enabled || prepare.busy || operation.blocked}
              >
                <FormField
                  id="manual-deposit-amount"
                  label="Deposit amount (PHP)"
                  value={amount}
                  onChange={setAmount}
                  inputMode="decimal"
                  maxLength={12}
                  placeholder="1,000.00"
                  hint={`From ${money(options.minAmount)} to ${money(options.manualMaxAmount)} per transaction.`}
                  required
                />
                {amount &&
                  (!validAmount(amount) ||
                    Number(amount) > options.manualMaxAmount) && (
                    <p className="error" role="alert">
                      Enter an amount from {money(options.minAmount)} to{" "}
                      {money(options.manualMaxAmount)}, with at most two
                      decimals.
                    </p>
                  )}
                <div className="field">
                  <label htmlFor="manual-deposit-method">Deposit method</label>
                  <select
                    id="manual-deposit-method"
                    value={method}
                    onChange={(event) => setMethod(event.target.value)}
                    required
                  >
                    <option value="">Choose a demo funding source</option>
                    {options.methods.map((value) => (
                      <option value={value.value} key={value.value}>
                        {value.label}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="field">
                  <label htmlFor="manual-deposit-notes">Notes (optional)</label>
                  <textarea
                    id="manual-deposit-notes"
                    value={note}
                    maxLength={150}
                    onChange={(event) => setNote(event.target.value)}
                    rows={3}
                    aria-describedby="manual-note-length"
                  />
                  <small id="manual-note-length">
                    {note.length}/150 characters
                  </small>
                  {/[\u0000-\u001f\u007f]/.test(note) && (
                    <p className="error" role="alert">
                      Use a single-line note without control characters.
                    </p>
                  )}
                </div>
                {validAmount(amount) && (
                  <p className="deposit-summary">
                    <span>Amount to review</span>
                    <strong>{money(Number(amount))}</strong>
                  </p>
                )}
                <ErrorNotice error={prepare.error || operation.error} />
                <button
                  className="btn"
                  disabled={!valid || prepare.busy}
                  aria-busy={prepare.busy}
                >
                  {prepare.busy && <Spinner />}
                  {prepare.busy ? "Preparing review…" : "Review Deposit"}
                </button>
              </fieldset>
            </form>
          )}
          {operation.blocked && <ErrorNotice error={operation.error} />}
        </>
      )}
    </section>
  );
}
