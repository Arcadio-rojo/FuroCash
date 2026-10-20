import { useEffect, useState } from "react";
export default function ResendCode({
  onResend,
  disabled = false,
}: {
  onResend: () => Promise<void>;
  disabled?: boolean;
}) {
  const [until, setUntil] = useState(() => Date.now() + 60000);
  const [now, setNow] = useState(() => Date.now());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  const seconds = Math.max(0, Math.ceil((until - now) / 1000));
  async function resend() {
    setBusy(true);
    setError("");
    try {
      await onResend();
      setUntil(Date.now() + 60000);
      setNow(Date.now());
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to resend.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="resend">
      <button
        className="link"
        type="button"
        disabled={disabled || busy || seconds > 0}
        onClick={resend}
      >
        {busy
          ? "Sending…"
          : seconds
            ? `Resend code in ${seconds}s`
            : "Resend code"}
      </button>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
