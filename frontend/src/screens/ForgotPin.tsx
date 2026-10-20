import { useState } from "react";
import type { FormEvent } from "react";
import { api } from "../api";
import AuthLayout from "../components/AuthLayout";
import FormField from "../components/FormField";
import { digits } from "../utils";
export default function ForgotPin({
  onCodeSent,
}: {
  onCodeSent: (mobile: string) => void;
}) {
  const [mobile, setMobile] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      await api.forgotPin(mobile);
      onCodeSent(mobile);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to send code.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <AuthLayout
      eyebrow="LET’S HELP YOU BACK IN"
      title="Forgot your PIN?"
      description="Enter your registered mobile number. We’ll send a code so you can set a new PIN."
    >
      <form onSubmit={submit}>
        <fieldset disabled={busy}>
          <FormField
            id="mobile"
            label="Mobile number"
            value={mobile}
            onChange={(v) => setMobile(digits(v))}
            inputMode="tel"
            autoComplete="tel"
            pattern="09[0-9]{9}"
            maxLength={11}
            placeholder="09XX XXX XXXX"
            required
          />
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          <button className="btn" type="submit">
            {busy ? "Sending code…" : "Send recovery code"}
            <span aria-hidden="true">↗</span>
          </button>
        </fieldset>
      </form>
      <p className="switch">
        <a href="#/login">Back to login</a>
      </p>
    </AuthLayout>
  );
}
