import { useRef, useState } from "react";
import { operationKey } from "../api";
import { errorMessage } from "../components/BankingUi";
export function useOperation() {
  const running = useRef(false);
  const keys = useRef(new Map<string, string>());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function run<T>(
    payload: unknown,
    action: (key: string) => Promise<T>,
    done: (value: T) => void,
  ) {
    if (running.current) return;
    running.current = true;
    setBusy(true);
    setError("");
    const fingerprint = JSON.stringify(payload);
    let key = keys.current.get(fingerprint);
    if (!key) {
      key = operationKey();
      keys.current.set(fingerprint, key);
    }
    try {
      const value = await action(key);
      keys.current.delete(fingerprint);
      done(value);
    } catch (e) {
      setError(
        `${errorMessage(e)} You can retry this same operation safely. If the response was lost, it may have completed.`,
      );
    } finally {
      running.current = false;
      setBusy(false);
    }
  }
  return { busy, error, setError, run };
}
