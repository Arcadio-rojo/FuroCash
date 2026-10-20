import { useState } from "react";
import type { FormEvent } from "react";
import { api } from "../api";
import AuthLayout from "../components/AuthLayout";
import FormField from "../components/FormField";
import { digits } from "../utils";
import ResendCode from "../components/ResendCode";
export default function ResetPin({
  mobile,
  onComplete,
}: {
  mobile: string;
  onComplete: () => void;
}) {
  const [otp, setOtp] = useState("");
  const [pin, setPin] = useState("");
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (busy) return;
    setError("");
    if (pin !== confirm) {
      setError("Your PINs do not match.");
      return;
    }
    setBusy(true);
    try {
      await api.resetPin(mobile, otp, pin);
      onComplete();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to reset PIN.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <AuthLayout
      eyebrow="A FRESH START"
      title="Choose a new PIN."
      description={`If ${mobile} is registered, a recovery code is on its way. Enter it below with your new PIN.`}
    >
      <p className="notice">
        Check your phone's SMS inbox for the recovery code.
      </p>
      <form onSubmit={submit}>
        <fieldset disabled={busy}>
          <FormField
            id="otp"
            label="Recovery code"
            value={otp}
            onChange={(v) => setOtp(digits(v))}
            inputMode="numeric"
            autoComplete="one-time-code"
            pattern="[0-9]{6}"
            maxLength={6}
            required
          />
          <div className="field-grid">
            <FormField
              id="pin"
              label="New PIN"
              type="password"
              value={pin}
              onChange={(v) => setPin(digits(v))}
              inputMode="numeric"
              autoComplete="new-password"
              pattern="[0-9]{4}"
              maxLength={4}
              required
            />
            <FormField
              id="confirm"
              label="Confirm PIN"
              type="password"
              value={confirm}
              onChange={(v) => setConfirm(digits(v))}
              inputMode="numeric"
              autoComplete="new-password"
              pattern="[0-9]{4}"
              maxLength={4}
              required
            />
          </div>
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          <button className="btn" type="submit">
            {busy ? "Updating PIN…" : "Reset PIN"}
            <span aria-hidden="true">↗</span>
          </button>
        </fieldset>
      </form>
      <ResendCode
        disabled={busy}
        onResend={async () => {
          await api.forgotPin(mobile);
        }}
      />
      <p className="switch">
        <a href="#/forgot-pin">Use another number</a>
      </p>
    </AuthLayout>
  );
}
