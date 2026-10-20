import { useState } from "react";
import { api } from "../api";
import type { Receipt } from "../api";
import FormField from "../components/FormField";
import {
  PageTitle,
  DemoNotice,
  ErrorNotice,
  PaymentReceipt,
  validAmount,
  amountError,
} from "../components/BankingUi";
import { money } from "../utils";
import { useOperation } from "./useOperation";
import { QrGenerator } from "./ScanPay";
import LoadingState, { Spinner } from "../components/LoadingState";
import ManualDeposit from "./ManualDeposit";
export function Deposit({
  demo,
  loading = false,
  onComplete,
}: {
  demo: boolean;
  loading?: boolean;
  onComplete: (balance: number) => void;
}) {
  const [amount, setAmount] = useState("");
  const [pending, setPending] = useState<Receipt | null>(null);
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [showQr, setShowQr] = useState(false);
  const op = useOperation();
  const stage = receipt ? 3 : pending ? 2 : 1;
  const steps = (
    <ol className="deposit-steps" aria-label="Deposit progress">
      {["Enter amount", "Review", "Complete"].map((label, index) => (
        <li
          key={label}
          className={index + 1 <= stage ? "reached" : ""}
          aria-current={index + 1 === stage ? "step" : undefined}
        >
          <span>{index + 1}</span>
          {label}
        </li>
      ))}
    </ol>
  );
  if (receipt)
    return (
      <>
        {steps}
        <PaymentReceipt receipt={receipt} title="Deposit complete" />
      </>
    );
  return (
    <>
      <PageTitle
        eyebrow="A LITTLE EXTRA ROOM"
        title={pending ? "Review deposit" : "Deposit"}
      >
        Add simulated funds to your FuroCash account.
      </PageTitle>
      {steps}
      <div className="payment-grid">
        <section className="bank-panel">
          <div className="method-heading">
            <span className="method-icon gcash-mark" aria-hidden="true">
              G
            </span>
            <div>
              <h2>GCash demo</h2>
              <p>Manual demo cash-in · PHP wallet</p>
            </div>
            <span className="badge">DEMO</span>
          </div>
          <DemoNotice />
          {loading ? (
            <LoadingState label="Checking deposit availability…" rows={2} />
          ) : !demo ? (
            <p className="notice">
              Demo deposits are currently unavailable. Try again when demo
              funding is enabled.
            </p>
          ) : pending ? (
            <>
              <dl className="detail-list">
                <div>
                  <dt>Method</dt>
                  <dd>GCash demo · Manual entry</dd>
                </div>
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
                <div>
                  <dt>Demo service fee</dt>
                  <dd>{money(0)}</dd>
                </div>
                <div>
                  <dt>To account</dt>
                  <dd>{pending.accountIdentifier}</dd>
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
                aria-busy={op.busy}
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
                {op.busy && <Spinner />}
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
                <h2>Manual demo deposit</h2>
                <p className="deposit-description">
                  Try the GCash-style cash-in experience with a custom amount.
                  No GCash app, payment, or external reference is needed.
                </p>
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
                <div
                  className="amount-presets"
                  role="group"
                  aria-label="Quick deposit amounts"
                >
                  {[100, 500, 1000, 5000].map((value) => (
                    <button
                      type="button"
                      className={Number(amount) === value ? "selected" : ""}
                      aria-pressed={Number(amount) === value}
                      aria-label={`Add ${value} pesos`}
                      key={value}
                      onClick={() => {
                        setAmount(value.toFixed(2));
                        op.setError("");
                      }}
                    >
                      {money(value)}
                    </button>
                  ))}
                </div>
                <div className="deposit-summary">
                  <span>Demo service fee</span>
                  <strong>{money(0)}</strong>
                </div>
                <p className="deposit-description">
                  Review your request, then simulate completion to add demo
                  funds to your FuroCash balance.
                </p>
                <ErrorNotice error={op.error} />
                <button className="btn" aria-busy={op.busy}>
                  {op.busy && <Spinner />}
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
      <ManualDeposit onComplete={onComplete} />
    </>
  );
}
export { default as Transfer } from "./Transfer";
