import { useEffect, useState, useSyncExternalStore } from "react";
import { getPendingRequests, subscribeRequests } from "../api";
import { Spinner } from "./LoadingState";
export default function NetworkActivity() {
  const pending = useSyncExternalStore(subscribeRequests, getPendingRequests);
  const active = pending > 0;
  const [slow, setSlow] = useState(false);
  useEffect(() => {
    if (!active) return;
    const timer = window.setTimeout(() => setSlow(true), 8000);
    return () => {
      window.clearTimeout(timer);
      setSlow(false);
    };
  }, [active]);
  if (!active) return null;
  return (
    <>
      <div
        className="network-progress"
        role="progressbar"
        aria-label="Loading FuroCash"
      >
        <span />
      </div>
      {slow && (
        <div className="connection-notice" role="status">
          <Spinner />
          <div>
            <strong>Still connecting</strong>
            <p>
              This is taking a little longer. Please keep this page open;
              there’s no need to submit again.
            </p>
          </div>
        </div>
      )}
    </>
  );
}
