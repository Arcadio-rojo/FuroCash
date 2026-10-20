import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import { api, tokenStore, ApiError } from "../api";
import type { LoginResponse, User } from "../api";
import { AuthContext } from "./session";
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(!!tokenStore.get());
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const expire = () => {
      setUser(null);
      setNotice("Your session ended. Please log in again.");
      location.hash = "/login";
    };
    const storage = (event: StorageEvent) => {
      if (event.key === "furocash_token") {
        setUser(null);
        setLoading(!!tokenStore.get());
        setAttempt((n) => n + 1);
      }
    };
    window.addEventListener("furocash:session-expired", expire);
    window.addEventListener("storage", storage);
    return () => {
      window.removeEventListener("furocash:session-expired", expire);
      window.removeEventListener("storage", storage);
    };
  }, []);
  useEffect(() => {
    const restoreToken = tokenStore.get();
    if (!restoreToken) return;
    const controller = new AbortController();
    api
      .me(controller.signal)
      .then((restored) => {
        if (!controller.signal.aborted && tokenStore.get() === restoreToken)
          setUser(restored);
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted || tokenStore.get() !== restoreToken)
          return;
        if (!(e instanceof ApiError && e.status === 401))
          setError(
            e instanceof Error ? e.message : "Unable to restore session.",
          );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [attempt]);
  const accept = (session: LoginResponse) => {
    tokenStore.set(session.token);
    setUser(session.user);
    setError("");
    setNotice("");
    location.hash = "/dashboard";
  };
  const logout = () => {
    tokenStore.clear();
    setUser(null);
    setError("");
    setNotice("");
    location.hash = "/login";
  };
  return (
    <AuthContext.Provider
      value={{
        user,
        loading,
        error,
        notice,
        accept,
        logout,
        updateUser: setUser,
        retry: () => {
          setLoading(true);
          setError("");
          setAttempt((n) => n + 1);
        },
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
