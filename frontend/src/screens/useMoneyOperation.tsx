import { useLayoutEffect, useRef, useState } from "react";
import { api, ApiError, operationKey, tokenStore } from "../api";
import type { Receipt, TransferInput, TransferReview } from "../api";
import { useAuth } from "../context/session";
import { errorMessage } from "../components/BankingUi";

type Intent =
  | { kind: "MANUAL_DEPOSIT"; payload: { reference: string }; preview: Receipt }
  | { kind: "TRANSFER"; payload: TransferInput; preview: TransferReview };
export type MoneyAttempt = Intent & { userId: number; key: string };
const journalKey = (userId: number) => `furocash_money_${userId}`;
function load(userId: number): {
  attempt: MoneyAttempt | null;
  storageError: string;
} {
  try {
    const raw = sessionStorage.getItem(journalKey(userId));
    if (!raw) return { attempt: null, storageError: "" };
    const value = JSON.parse(raw);
    if (
      value.userId !== userId ||
      !["MANUAL_DEPOSIT", "TRANSFER"].includes(value.kind) ||
      typeof value.key !== "string" ||
      !value.payload ||
      !value.preview
    )
      throw new Error();
    return { attempt: value, storageError: "" };
  } catch {
    return {
      attempt: null,
      storageError:
        "Transaction recovery storage is unavailable. Enable session storage before starting a new transaction; check history for any unfinished attempt.",
    };
  }
}
async function timed<T>(
  action: (signal: AbortSignal) => Promise<T>,
  milliseconds: number,
): Promise<T> {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), milliseconds);
  try {
    return await action(controller.signal);
  } finally {
    window.clearTimeout(timer);
  }
}

/** Persists an immutable attempt before sending money. Unknown outcomes never get a new key. */
export function useMoneyOperation(onComplete: (receipt: Receipt) => void) {
  const { user } = useAuth();
  const userId = user!.id;
  const [initial] = useState(() => load(userId));
  const [attempt, setAttempt] = useState<MoneyAttempt | null>(initial.attempt);
  const attemptRef = useRef(initial.attempt);
  const [error, setError] = useState(initial.storageError);
  const [busy, setBusy] = useState(false);
  const running = useRef(false);
  const [needsReview, setNeedsReview] = useState(false);
  const session = useRef({ mounted: false, userId });
  useLayoutEffect(() => {
    const value = session.current;
    value.mounted = true;
    value.userId = userId;
    return () => {
      value.mounted = false;
    };
  }, [userId]);
  function save(value: MoneyAttempt | null) {
    if (value)
      sessionStorage.setItem(journalKey(userId), JSON.stringify(value));
    else sessionStorage.removeItem(journalKey(userId));
    attemptRef.current = value;
    setAttempt(value);
  }
  async function execute(
    value: MoneyAttempt,
    checkOnly: boolean,
    first = false,
  ) {
    if (running.current) return;
    running.current = true;
    setBusy(true);
    setError("");
    setNeedsReview(false);
    const token = tokenStore.get();
    const current = () =>
      session.current.mounted &&
      session.current.userId === value.userId &&
      tokenStore.get() === token;
    const finish = (receipt: Receipt) => {
      if (
        receipt.status !== "COMPLETED" ||
        !receipt.reference ||
        !receipt.createdAt
      )
        throw new Error(
          "The server has not confirmed a completed transaction.",
        );
      if (current()) {
        save(null);
        onComplete(receipt);
      }
    };
    const recover = () =>
      timed((signal) => api.operationStatus(value.key, signal), 10000);
    try {
      if (!first) {
        const result = await recover();
        if (!current()) return;
        if (result.state === "COMPLETED" && result.receipt) {
          finish(result.receipt);
          return;
        }
        if (result.state === "PROCESSING") {
          setError(
            "Your original request is still processing. Check its status again; do not start a new transaction.",
          );
          return;
        }
        if (checkOnly) {
          setError(
            "No completed transaction was found yet. Retry the same request safely using its original key.",
          );
          return;
        }
      }
      const result = await timed(
        (signal) =>
          value.kind === "MANUAL_DEPOSIT"
            ? api.completeDeposit(value.payload.reference, value.key, signal)
            : api.send(value.payload, value.key, signal),
        30000,
      );
      finish(result);
    } catch (cause) {
      if (!current()) return;
      if (
        cause instanceof ApiError &&
        [400, 403, 404, 409, 422].includes(cause.status) &&
        !cause.outcomeUnknown
      ) {
        save(null);
        setNeedsReview(true);
        setError(
          `${errorMessage(cause)} Please review the details again before confirming.`,
        );
      } else {
        try {
          const result = await recover();
          if (!current()) return;
          if (result.state === "COMPLETED" && result.receipt) {
            finish(result.receipt);
            return;
          }
        } catch {
          /* Keep the journal even when reconciliation cannot reach the server. */
        }
        if (current())
          setError(
            "The outcome is not confirmed yet. Check transaction status or retry the same request. Its original key is saved, so it cannot be processed twice.",
          );
      }
    } finally {
      running.current = false;
      if (current()) setBusy(false);
    }
  }
  async function confirm(intent: Intent) {
    if (running.current || initial.storageError) return;
    const saved = attemptRef.current;
    if (saved) {
      if (
        saved.kind !== intent.kind ||
        JSON.stringify(saved.payload) !== JSON.stringify(intent.payload)
      ) {
        setError("Resolve the saved transaction before starting another one.");
        return;
      }
      await execute(saved, false);
      return;
    }
    const value = JSON.parse(
      JSON.stringify({ ...intent, userId, key: operationKey() }),
    ) as MoneyAttempt;
    try {
      save(value);
    } catch {
      setError(
        "Could not save recovery information. No transaction was submitted.",
      );
      return;
    }
    await execute(value, false, true);
  }
  return {
    attempt,
    busy,
    error,
    setError,
    needsReview,
    blocked: !!initial.storageError,
    confirm,
    resetReview: () => {
      if (!attemptRef.current) {
        setError("");
        setNeedsReview(false);
      }
    },
    check: () =>
      attemptRef.current
        ? execute(attemptRef.current, true)
        : Promise.resolve(),
    retry: () =>
      attemptRef.current
        ? execute(attemptRef.current, false)
        : Promise.resolve(),
  };
}

export function MoneyRecovery({
  operation,
  allowRetry = true,
}: {
  operation: ReturnType<typeof useMoneyOperation>;
  allowRetry?: boolean;
}) {
  if (!operation.attempt) return null;
  return (
    <div className="money-recovery" role="status">
      <strong>Saved transaction in progress</strong>
      <p>
        We’ll check the original request before doing anything else. Please keep
        this tab open.
      </p>
      <div className="button-row">
        <button
          className="secondary"
          disabled={operation.busy}
          onClick={() => void operation.check()}
        >
          Check transaction status
        </button>
        <button
          className="secondary"
          disabled={operation.busy || !allowRetry}
          onClick={() => void operation.retry()}
        >
          Retry same request
        </button>
      </div>
      {operation.busy && <p>Checking or processing your saved request…</p>}
    </div>
  );
}
