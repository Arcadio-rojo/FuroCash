import { useState } from "react";
import type { FormEvent } from "react";
import { api } from "../api";
import AuthLayout from "../components/AuthLayout";
import FormField from "../components/FormField";
import { digits } from "../utils";
export interface Registration {
  mobile: string;
  pin: string;
  fullName: string;
}
export default function Register({
  onCodeSent,
}: {
  onCodeSent: (data: Registration) => void;
}) {
  const [fullName, setName] = useState("");
  const [mobile, setMobile] = useState("");
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
    if (!fullName.trim()) {
      setError("Enter your full name.");
      return;
    }
    setBusy(true);
    try {
      await api.requestRegistrationCode(mobile, pin, fullName.trim());
      onCodeSent({ mobile, pin, fullName: fullName.trim() });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to send code.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <AuthLayout
      eyebrow="LET’S GET YOU STARTED"
      title="Make room for more."
      description="Create your account. A few details, and you’re on your way."
    >
      <form onSubmit={submit}>
        <fieldset disabled={busy}>
          <FormField
            id="fullName"
            label="Full name"
            value={fullName}
            onChange={setName}
            maxLength={100}
            autoComplete="name"
            placeholder="Your full name"
            required
          />
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
          <div className="field-grid">
            <FormField
              id="pin"
              label="Create PIN"
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
            {busy ? "Sending code…" : "Continue"}
            <span aria-hidden="true">↗</span>
          </button>
        </fieldset>
      </form>
      <p className="switch">
        Already with us? <a href="#/login">Log in</a>
      </p>
    </AuthLayout>
  );
}
