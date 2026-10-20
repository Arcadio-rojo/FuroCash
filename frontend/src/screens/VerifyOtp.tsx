import { useState } from "react";
import type { FormEvent } from "react";
import { api } from "../api";
import { useAuth } from "../context/session";
import AuthLayout from "../components/AuthLayout";
import FormField from "../components/FormField";
import { digits } from "../utils";
import ResendCode from "../components/ResendCode";
import type { Registration } from "./Register";
export default function VerifyOtp({
  registration,
  onComplete,
}: {
  registration: Registration;
  onComplete: () => void;
}) {
  const { accept } = useAuth();
  const [otp, setOtp] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      const session = await api.register(
        registration.mobile,
        registration.pin,
        registration.fullName,
        otp,
      );
      accept(session);
      onComplete();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to verify code.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <AuthLayout
      eyebrow="ONE LAST STEP"
      title="Let’s make it official."
      description={`Enter the 6-digit code sent to ${registration.mobile}. It expires in 5 minutes.`}
    >
      <p className="notice">
        Check your phone's SMS inbox. Messages can take a moment to arrive.
      </p>
      <form onSubmit={submit}>
        <fieldset disabled={busy}>
          <FormField
            id="otp"
            label="Verification code"
            value={otp}
            onChange={(v) => setOtp(digits(v))}
            inputMode="numeric"
            autoComplete="one-time-code"
            pattern="[0-9]{6}"
            maxLength={6}
            placeholder="000000"
            required
          />
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          <button className="btn" type="submit">
            {busy ? "Verifying…" : "Verify & create account"}
            <span aria-hidden="true">↗</span>
          </button>
        </fieldset>
      </form>
      <ResendCode
        disabled={busy}
        onResend={async () => {
          await api.requestRegistrationCode(
            registration.mobile,
            registration.pin,
            registration.fullName,
          );
        }}
      />
      <p className="switch">
        <a href="#/register">Change your details</a>
      </p>
    </AuthLayout>
  );
}
