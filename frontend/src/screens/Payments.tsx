import { useState } from "react";
import { api } from "../api";
import type { Receipt, Recipient, ResolvedQr } from "../api";
import FormField from "../components/FormField";
import {
  PageTitle,
  DemoNotice,
  ErrorNotice,
  PaymentReceipt,
  validAmount,
  amountError,
  errorMessage,
} from "../components/BankingUi";
import { money } from "../utils";
import { useOperation } from "./useOperation";
import { QrGenerator } from "./ScanPay";
export function Deposit({
  demo,
  onComplete,
}: {
  demo: boolean;
  onComplete: (balance: number) => void;
}) {
  const [amount, setAmount] = useState("");
  const [pending, setPending] = useState<Receipt | null>(null);
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [showQr, setShowQr] = useState(false);
  const op = useOperation();
  if (receipt)
    return <PaymentReceipt receipt={receipt} title="Deposit complete" />;
  return (
    <>
      <PageTitle
        eyebrow="A LITTLE EXTRA ROOM"
        title={pending ? "Review deposit" : "Deposit"}
      >
        Add simulated funds to your FuroCash account.
      </PageTitle>
      <div className="payment-grid">
        <section className="bank-panel">
          <div className="method-heading">
            <span className="method-icon">G</span>
            <div>
              <h2>GCash demo</h2>
              <p>Simulated cash-in · No real money</p>
            </div>
            <span className="badge">DEMO</span>
          </div>
          <DemoNotice />
          {!demo ? (
            <p className="notice">
              Demo deposits are currently unavailable. Try again when demo
              funding is enabled.
            </p>
          ) : pending ? (
            <>
              <dl className="detail-list">
                <div>
                  <dt>Amount</dt>
                  <dd>{money(pending.amount)}</dd>
                </div>
                <div>
                  <dt>Reference</dt>
                  <dd>{pending.reference}</dd>
                </div>
                <div>
                  <dt>Status</dt>
                  <dd>{pending.status}</dd>
                </div>
              </dl>
              <p className="lede">
                Your balance changes only after you explicitly simulate
                completion.
              </p>
              <ErrorNotice error={op.error} />
              <button
                className="btn"
                disabled={op.busy}
                onClick={() =>
                  void op.run(
                    { complete: pending.reference },
                    (key) => api.completeDeposit(pending.reference, key),
                    (value) => {
                      setReceipt(value);
                      onComplete(value.balance);
                    },
                  )
                }
              >
                {op.busy ? "Completing…" : "Simulate completion"}
              </button>
            </>
          ) : (
            <form
              onSubmit={(event) => {
                event.preventDefault();
                if (!validAmount(amount)) {
                  op.setError(amountError);
                  return;
                }
                void op.run(
                  { prepare: amount },
                  (key) => api.prepareDeposit(amount, key),
                  setPending,
                );
              }}
            >
              <fieldset disabled={op.busy}>
                <FormField
                  id="deposit-amount"
                  label="Amount (PHP)"
                  value={amount}
                  onChange={setAmount}
                  inputMode="decimal"
                  placeholder="0.00"
                  maxLength={13}
                  required
                />
                <ErrorNotice error={op.error} />
                <button className="btn">
                  {op.busy ? "Preparing…" : "Review deposit"}
                </button>
              </fieldset>
            </form>
          )}
        </section>
        <section className="bank-panel soft-panel">
          <span className="eyebrow">ANOTHER WAY TO EXPLORE</span>
          <h2>Your demo deposit QR</h2>
          <p>
            Save a QR for this account. This custom FuroCash code is not a GCash
            or QR Ph payment code.
          </p>
          {showQr ? (
            <QrGenerator purpose="DEPOSIT" />
          ) : (
            <button className="secondary" onClick={() => setShowQr(true)}>
              Create deposit QR
            </button>
          )}
        </section>
      </div>
    </>
  );
}
export function Transfer({
  initial,
  onComplete,
}: {
  initial: ResolvedQr | null;
  onComplete: (balance: number) => void;
}) {
  const [identifier, setIdentifier] = useState(
    initial?.recipient.accountIdentifier ?? "",
  );
  const [recipient, setRecipient] = useState<Recipient | null>(
    initial?.recipient ?? null,
  );
  const [amount, setAmount] = useState(initial?.amount ?? "");
  const [note, setNote] = useState("");
  const [review, setReview] = useState(false);
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [finding, setFinding] = useState(false);
  const [lookupError, setLookupError] = useState("");
  const op = useOperation();
  async function lookup() {
    if (finding) return;
    setFinding(true);
    setLookupError("");
    try {
      setRecipient(await api.recipient(identifier.trim().toUpperCase()));
    } catch (e) {
      setLookupError(errorMessage(e));
    } finally {
      setFinding(false);
    }
  }
  if (receipt)
    return <PaymentReceipt receipt={receipt} title="Transfer complete" />;
  const input = {
    receiverAccount: recipient?.accountIdentifier,
    amount,
    note: note.trim(),
    ...(initial ? { qrReference: initial.reference } : {}),
  };
  return (
    <>
      <PageTitle
        eyebrow="MAKE SOMEONE’S DAY"
        title={review ? "Confirm transfer" : "Transfer"}
      >
        Send to another FuroCash account.
      </PageTitle>
      <section className="bank-panel form-panel">
        {review && recipient ? (
          <>
            <p className="review-label">You’re sending</p>
            <p className="review-amount">{money(Number(amount))}</p>
            <dl className="detail-list">
              <div>
                <dt>To</dt>
                <dd>{recipient.fullName}</dd>
              </div>
              <div>
                <dt>Account</dt>
                <dd>{recipient.accountIdentifier}</dd>
              </div>
              <div>
                <dt>Mobile</dt>
                <dd>{recipient.mobileNumber}</dd>
              </div>
              {note && (
                <div>
                  <dt>Note</dt>
                  <dd>{note}</dd>
                </div>
              )}
            </dl>
            <p className="lede">
              Check the recipient and amount. Confirmed transfers are immediate.
            </p>
            <ErrorNotice error={op.error} />
            <button
              className="btn"
              disabled={op.busy}
              onClick={() =>
                void op.run(
                  input,
                  (key) => api.send(input, key),
                  (value) => {
                    setReceipt(value);
                    onComplete(value.balance);
                  },
                )
              }
            >
              {op.busy ? "Sending…" : "Confirm & send"}
            </button>
            <button
              className="link back-button"
              disabled={op.busy}
              onClick={() => {
                setReview(false);
                op.setError("");
              }}
            >
              Edit details
            </button>
          </>
        ) : (
          <>
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
                >
                  {finding ? "Finding…" : "Find recipient"}
                </button>
              </fieldset>
            </form>
            <ErrorNotice error={lookupError} />
            {recipient && (
              <>
                <div className="recipient-found">
                  <span className="avatar">{recipient.fullName[0]}</span>
                  <div>
                    <strong>{recipient.fullName}</strong>
                    <small>
                      {recipient.accountIdentifier} · {recipient.mobileNumber}
                    </small>
                  </div>
                  <span className="badge">FOUND</span>
                </div>
                <form
                  onSubmit={(event) => {
                    event.preventDefault();
                    if (!validAmount(amount)) {
                      op.setError(amountError);
                      return;
                    }
                    op.setError("");
                    setReview(true);
                  }}
                >
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
                  <ErrorNotice error={op.error} />
                  <button className="btn">Review transfer</button>
                </form>
              </>
            )}
          </>
        )}
        <DemoNotice />
      </section>
    </>
  );
}
