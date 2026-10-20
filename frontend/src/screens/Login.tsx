import { useState } from "react";
import type { FormEvent } from "react";
import { api } from "../api";
import { useAuth } from "../context/session";
import AuthLayout from "../components/AuthLayout";
import FormField from "../components/FormField";
import { digits } from "../utils";
export default function Login({ message = "" }: { message?: string }) {
  const { accept, notice } = useAuth();
  const [mobile, setMobile] = useState("");
  const [pin, setPin] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      accept(await api.login(mobile, pin));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to log in.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <AuthLayout
      eyebrow="WELCOME BACK"
      title="Good to see you again."
      description="Log in to your FuroCash account to get started."
    >
      {(message || notice) && (
        <p className="notice" role="status">
          {message || notice}
        </p>
      )}
      <form onSubmit={submit}>
        <fieldset disabled={busy}>
          <FormField
            id="mobile"
            label="Mobile number"
            value={mobile}
            onChange={(v) => setMobile(digits(v))}
            inputMode="tel"
            autoComplete="tel"
            placeholder="09XX XXX XXXX"
            pattern="09[0-9]{9}"
            minLength={11}
            maxLength={11}
            required
            hint="Your 11-digit Philippine mobile number."
          />
          <FormField
            id="pin"
            label="4-digit PIN"
            type="password"
            value={pin}
            onChange={(v) => setPin(digits(v))}
            inputMode="numeric"
            autoComplete="current-password"
            pattern="[0-9]{4}"
            minLength={4}
            maxLength={4}
            required
          />
          <div className="form-link">
            <a href="#/forgot-pin">Forgot your PIN?</a>
          </div>
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          <button className="btn" type="submit">
            {busy ? "Logging in…" : "Log in to FuroCash"}
            <span aria-hidden="true">↗</span>
          </button>
        </fieldset>
      </form>
      <p className="switch">
        New here? <a href="#/register">Create an account</a>
      </p>
      <div className="form-divider" />
      <p className="subtle">
        Your PIN stays private. Never share it with anyone.
      </p>
    </AuthLayout>
  );
}
