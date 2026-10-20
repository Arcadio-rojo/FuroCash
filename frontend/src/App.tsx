import { useEffect, useState } from "react";
import { AuthProvider } from "./context/AuthContext";
import { useLayoutEffect } from "react";
import { initializeTheme } from "./theme";
import ThemeToggle from "./components/ThemeToggle";
import NetworkActivity from "./components/NetworkActivity";
import LoadingState from "./components/LoadingState";
import { useAuth } from "./context/session";
import Login from "./screens/Login";
import Register from "./screens/Register";
import type { Registration } from "./screens/Register";
import VerifyOtp from "./screens/VerifyOtp";
import ForgotPin from "./screens/ForgotPin";
import ResetPin from "./screens/ResetPin";
import Dashboard from "./screens/Dashboard";
import ProtectedRoute from "./components/ProtectedRoute";
function Screens() {
  const { user, loading, error, retry, logout } = useAuth();
  const [route, setRoute] = useState(() => location.hash.slice(1) || "/login");
  const [registration, setRegistration] = useState<Registration | null>(null);
  const [recoveryMobile, setRecoveryMobile] = useState("");
  const [message, setMessage] = useState("");
  useEffect(() => {
    const change = () => {
      const next = location.hash.slice(1) || "/login";
      setRoute(next);
      if (next !== "/verify" && next !== "/register") setRegistration(null);
    };
    window.addEventListener("hashchange", change);
    return () => window.removeEventListener("hashchange", change);
  }, []);
  useEffect(() => {
    document.title = `${user ? "Dashboard" : route === "/register" ? "Create account" : route.includes("pin") ? "PIN recovery" : "Welcome"} · FuroCash`;
  }, [user, route]);
  if (loading)
    return (
      <main className="center-state">
        <span className="brand-mark">f.</span>
        <LoadingState label="Getting your account ready…" rows={2} />
        <ThemeToggle />
      </main>
    );
  if (error)
    return (
      <main className="center-state">
        <h1>Let’s reconnect.</h1>
        <ThemeToggle />
        <p className="error" role="alert">
          {error}
        </p>
        <button className="btn" onClick={retry}>
          Try again
        </button>
        <button className="link" onClick={logout}>
          Return to login
        </button>
      </main>
    );
  if (user && route !== "/forgot-pin" && route !== "/reset-pin")
    return (
      <ProtectedRoute>
        <Dashboard route={route} />
      </ProtectedRoute>
    );
  if (route === "/register")
    return (
      <Register
        onCodeSent={(data) => {
          setRegistration(data);
          location.hash = "/verify";
        }}
      />
    );
  if (route === "/verify" && registration)
    return (
      <VerifyOtp
        registration={registration}
        onComplete={() => setRegistration(null)}
      />
    );
  if (route === "/forgot-pin")
    return (
      <ForgotPin
        onCodeSent={(mobile) => {
          setRecoveryMobile(mobile);
          location.hash = "/reset-pin";
        }}
      />
    );
  if (route === "/reset-pin" && recoveryMobile)
    return (
      <ResetPin
        mobile={recoveryMobile}
        onComplete={() => {
          setRecoveryMobile("");
          setMessage("PIN updated. Log in with your new PIN.");
          location.hash = "/login";
        }}
      />
    );
  return <Login message={message} />;
}
export default function App() {
  useLayoutEffect(initializeTheme, []);
  return (
    <>
      <NetworkActivity />
      <AuthProvider>
        <Screens />
      </AuthProvider>
    </>
  );
}
