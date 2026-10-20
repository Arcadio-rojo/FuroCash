import { useEffect, useRef, useState } from "react";
import QRCode from "qrcode";
import QrScanner from "qr-scanner";
import { api } from "../api";
import type { QrPayload, ResolvedQr } from "../api";
import { parseQr, encodeQr } from "../qr";
import FormField from "../components/FormField";
import {
  PageTitle,
  DemoNotice,
  ErrorNotice,
  errorMessage,
  validAmount,
  amountError,
} from "../components/BankingUi";
export function QrGenerator({ purpose }: { purpose: QrPayload["purpose"] }) {
  const [amount, setAmount] = useState("");
  const [image, setImage] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function generate() {
    if (busy) return;
    if (amount && !validAmount(amount)) {
      setError(amountError);
      return;
    }
    setBusy(true);
    setError("");
    try {
      const payload = await api.createQr(purpose, amount || null);
      setImage(
        await QRCode.toDataURL(encodeQr(payload), {
          width: 400,
          margin: 3,
          errorCorrectionLevel: "M",
        }),
      );
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="qr-generator">
      <DemoNotice />
      <FormField
        id={`qr-${purpose}`}
        label="Fixed amount (PHP, optional)"
        value={amount}
        onChange={setAmount}
        inputMode="decimal"
        maxLength={13}
        placeholder="Leave blank for any amount"
      />
      <button
        className="secondary"
        disabled={busy}
        onClick={() => void generate()}
      >
        {busy ? "Creating…" : "Generate QR"}
      </button>
      <ErrorNotice error={error} />
      {image && (
        <>
          <img
            className="qr-image"
            src={image}
            alt={`FuroCash demo ${purpose.toLowerCase()} QR`}
          />
          <p className="subtle">
            Valid for 24 hours.{" "}
            {purpose === "TRANSFER"
              ? "Can be paid once."
              : "For demo deposits only."}
          </p>
          <a
            className="btn"
            href={image}
            download={`furocash-demo-${purpose.toLowerCase()}.png`}
          >
            Save QR image
          </a>
        </>
      )}
    </div>
  );
}
export default function ScanPay({
  onResolved,
}: {
  onResolved: (value: ResolvedQr) => void;
}) {
  const video = useRef<HTMLVideoElement>(null);
  const scanner = useRef<QrScanner | null>(null);
  const resolving = useRef(false);
  const alive = useRef(true);
  const [camera, setCamera] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [text, setText] = useState("");
  function stop() {
    scanner.current?.destroy();
    scanner.current = null;
    setCamera(false);
  }
  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
      scanner.current?.destroy();
      scanner.current = null;
    };
  }, []);
  async function resolve(raw: string) {
    if (resolving.current) return;
    resolving.current = true;
    setBusy(true);
    setError("");
    try {
      const payload = parseQr(raw);
      const result = await api.resolveQr(payload);
      if (result.purpose !== "TRANSFER")
        throw new Error(
          "This QR is for demo deposits. Open Deposit to add demo funds.",
        );
      if (alive.current) {
        stop();
        onResolved(result);
      }
    } catch (e) {
      if (alive.current) setError(errorMessage(e));
    } finally {
      resolving.current = false;
      if (alive.current) setBusy(false);
    }
  }
  async function start() {
    if (scanner.current) return;
    setError("");
    setCamera(true);
    try {
      if (!video.current) throw new Error("Camera preview unavailable.");
      const next = new QrScanner(
        video.current,
        (result) => {
          void resolve(result.data);
        },
        {
          returnDetailedScanResult: true,
          highlightScanRegion: true,
          preferredCamera: "environment",
        },
      );
      scanner.current = next;
      await next.start();
      if (!alive.current) next.destroy();
    } catch (e) {
      stop();
      setError(
        `Camera unavailable. You can upload a QR image. ${errorMessage(e)}`,
      );
    }
  }
  async function upload(file: File | undefined) {
    if (!file || busy) return;
    setError("");
    try {
      if (file.size > 10 * 1024 * 1024)
        throw new Error("Choose an image smaller than 10 MB.");
      const result = await QrScanner.scanImage(file, {
        returnDetailedScanResult: true,
      });
      await resolve(result.data);
    } catch (e) {
      setError(`Could not read this QR image. ${errorMessage(e)}`);
    }
  }
  return (
    <>
      <PageTitle eyebrow="A QUICK WAY TO CONNECT" title="Scan & Pay">
        Scan a FuroCash demo QR, then review before sending.
      </PageTitle>
      <div className="payment-grid">
        <section className="bank-panel">
          <h2>Scan a payment QR</h2>
          <DemoNotice />
          <div className={`camera-preview ${camera ? "camera-active" : ""}`}>
            <video
              ref={video}
              muted
              playsInline
              aria-label="QR camera preview"
              hidden={!camera}
            />
            {!camera && <span>Camera starts only when you choose.</span>}
          </div>
          <div className="button-row">
            {camera ? (
              <button className="secondary" onClick={stop}>
                Stop camera
              </button>
            ) : (
              <button
                className="btn"
                disabled={busy}
                onClick={() => void start()}
              >
                Start camera
              </button>
            )}
            <label className="secondary upload-button">
              Upload QR image
              <input
                type="file"
                accept="image/*"
                disabled={busy}
                onChange={(e) => {
                  void upload(e.target.files?.[0]);
                  e.target.value = "";
                }}
              />
            </label>
          </div>
          <details>
            <summary>Paste QR contents</summary>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                void resolve(text);
              }}
            >
              <label htmlFor="qr-text">FuroCash QR JSON</label>
              <textarea
                id="qr-text"
                value={text}
                onChange={(e) => setText(e.target.value)}
                maxLength={2048}
                required
              />
              <button className="secondary" disabled={busy}>
                Read QR
              </button>
            </form>
          </details>
          <a className="secondary" href="#/transfer">Enter account details manually</a>
          {busy && <p role="status">Checking the persisted request…</p>}
          <ErrorNotice error={error} />
          <p className="subtle">
            Only custom FuroCash codes are supported. Scanning never moves
            money.
          </p>
        </section>
        <section className="bank-panel soft-panel">
          <h2>Receive with your QR</h2>
          <p>Create a request for your FuroCash account.</p>
          <QrGenerator purpose="TRANSFER" />
        </section>
      </div>
    </>
  );
}
