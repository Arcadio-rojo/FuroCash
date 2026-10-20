import type { ReactNode } from "react";
import { useAuth } from "../context/session";
export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  return user ? (
    children
  ) : (
    <main className="center-state">
      <h1>Log in to continue</h1>
      <a className="btn" href="#/login">
        Go to login
      </a>
    </main>
  );
}
