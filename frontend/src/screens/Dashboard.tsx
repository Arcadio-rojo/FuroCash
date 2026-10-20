import { useCallback, useEffect, useState } from "react";
import { api } from "../api";
import { useAuth } from "../context/session";
import BalanceCard from "../components/BalanceCard";
import TransactionList from "../components/TransactionList";
import { Icon, menu } from "../components/BankingUi";
import { Deposit, Transfer } from "./Payments";
import ScanPay from "./ScanPay";
import CardPage from "./CardPage";
import ProfilePage from "./ProfilePage";
import type { ResolvedQr, Transaction } from "../api";

export default function Dashboard({ route = "/home" }: { route?: string }) {
  const { user, logout } = useAuth();
  const [balance, setBalance] = useState(user?.balance ?? 0);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);
  const [hidden, setHidden] = useState(false);
  const [demo, setDemo] = useState(false);
  const [qr, setQr] = useState<ResolvedQr | null>(null);
  const active = menu.find((item) => item.path === route) ?? menu[0];
  const refresh = useCallback(async () => {
    try {
      const [account, history, health] = await Promise.all([
        api.balance(),
        api.transactions(page, 10),
        api.health(),
      ]);
      setBalance(account.balance);
      setTransactions(history);
      setDemo(health.demoCashIn);
      setError("");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not load your account.");
    } finally {
      setLoading(false);
    }
  }, [page]);
  useEffect(() => {
    void refresh();
  }, [refresh]);
  useEffect(() => {
    if (route !== "/scan" && route !== "/transfer") setQr(null);
  }, [route]);
  if (!user) return null;
  const onComplete = (next: number) => {
    setBalance(next);
    void refresh();
  };
  const nav = (mobile = false) => (
    <nav
      className={mobile ? "bottom-nav" : "bank-nav"}
      aria-label={mobile ? "Mobile navigation" : "Main navigation"}
    >
      {menu.map((item) => (
        <a
          key={item.path}
          href={`#${item.path}`}
          className={active.path === item.path ? "active" : ""}
          aria-current={active.path === item.path ? "page" : undefined}
        >
          <Icon name={item.icon} />
          <span>{item.label}</span>
        </a>
      ))}
    </nav>
  );
  return (
    <div className="bank-shell">
      <a className="skip-link" href="#main-content">
        Skip to content
      </a>
      <aside className="bank-sidebar">
        <a className="brand" href="#/home">
          <span className="brand-mark">f.</span>FuroCash
        </a>
        <span className="nav-label">YOUR EVERYDAY, SIMPLIFIED</span>
        {nav()}
        <div className="sidebar-bottom">
          <div className="demo-note">
            <span className="badge">YOUR DEMO SPACE</span>
            <p>
              A little room to explore.
              <br />
              All funds are simulated.
            </p>
          </div>
          <button className="logout" onClick={logout}>
            Log out <Icon name="arrow" />
          </button>
        </div>
      </aside>
      <div className="bank-main">
        <header className="bank-header">
          <a className="brand mobile-bank-brand" href="#/home">
            <span className="brand-mark">f.</span>FuroCash
          </a>
          <span className="desktop-crumb">
            Your space /{" "}
            <strong>
              {route === "/deposit"
                ? "Deposit"
                : route === "/transfer"
                  ? "Transfer"
                  : active.label}
            </strong>
          </span>
          <div className="header-right">
            <span className="badge">DEMO ACCOUNT</span>
            <a className="profile-link" href="#/me" aria-label="Open profile">
              <span className="avatar">
                {user.fullName.charAt(0).toUpperCase()}
              </span>
              <span>{user.fullName}</span>
            </a>
          </div>
        </header>
        <main id="main-content" className="bank-content" tabIndex={-1}>
          {error && (
            <div className="error" role="alert">
              {error}{" "}
              <button className="link" onClick={() => void refresh()}>
                Retry account
              </button>
            </div>
          )}
          {route === "/deposit" ? (
            <Deposit demo={demo} onComplete={onComplete} />
          ) : route === "/transfer" ? (
            <Transfer
              initial={qr}
              onComplete={(value) => {
                onComplete(value);
                setQr(null);
              }}
            />
          ) : route === "/scan" ? (
            <ScanPay
              onResolved={(value) => {
                setQr(value);
                location.hash = "/transfer";
              }}
            />
          ) : route === "/card" ? (
            <CardPage balance={balance} onComplete={onComplete} />
          ) : route === "/me" ? (
            <ProfilePage />
          ) : (
            <>
              {route !== "/transactions" && (
                <>
                  <div className="page-heading">
                    <div>
                      <span className="eyebrow">
                        A LITTLE CLARITY FOR YOUR DAY
                      </span>
                      <h1>
                        Hello, {user.fullName.split(" ")[0]}
                        <span className="greeting-dot">.</span>
                      </h1>
                      <p>Good things start with a clear view.</p>
                    </div>
                    <button
                      className="icon-button"
                      aria-label="Refresh account"
                      disabled={loading}
                      onClick={() => void refresh()}
                    >
                      <Icon name="refresh" />
                    </button>
                  </div>
                  <div className="home-grid">
                    <div className="home-balance">
                      <BalanceCard
                        balance={balance}
                        hidden={hidden}
                        onToggle={() => setHidden(!hidden)}
                      />
                      <div className="account-caption">
                        FuroCash account{" "}
                        <span>
                          {hidden
                            ? "•••• •••• ••••"
                            : `•••• •••• ${user.accountIdentifier?.slice(-4) ?? String(user.id).padStart(4, "0")}`}
                        </span>
                      </div>
                      <div className="bank-actions">
                        {[
                          { label: "Deposit", path: "/deposit", icon: "plus" },
                          {
                            label: "Transfer",
                            path: "/transfer",
                            icon: "arrow",
                          },
                          {
                            label: "History",
                            path: "/transactions",
                            icon: "history",
                          },
                          { label: "Scan", path: "/scan", icon: "scan" },
                        ].map((action) => (
                          <a key={action.path} href={`#${action.path}`}>
                            <span>
                              <Icon name={action.icon} />
                            </span>
                            {action.label}
                          </a>
                        ))}
                      </div>
                    </div>
                    <section className="home-feature">
                      <span className="badge">MADE FOR YOUR EVERYDAY</span>
                      <div className="feature-art" aria-hidden="true">
                        <span>f.</span>
                        <Icon name="spark" />
                      </div>
                      <h2>
                        Your money.
                        <br />A little more in focus.
                      </h2>
                      <p>
                        Send, scan, and explore your demo card. Everything in
                        one familiar space.
                      </p>
                      <a href="#/card">
                        Meet your demo card <Icon name="arrow" />
                      </a>
                    </section>
                  </div>
                </>
              )}
              <section className="activity-panel">
                <div className="section-heading">
                  <div>
                    <span className="eyebrow">EVERY MOVE, IN ONE PLACE</span>
                    <h1
                      className={
                        route === "/transactions" ? "" : "small-heading"
                      }
                    >
                      {route === "/transactions"
                        ? "Transactions"
                        : "Recent activity"}
                    </h1>
                  </div>
                  {route !== "/transactions" && (
                    <a href="#/transactions">
                      View all <span aria-hidden="true">↗</span>
                    </a>
                  )}
                </div>
                {loading ? (
                  <p role="status" className="loading">
                    Loading your activity…
                  </p>
                ) : (
                  <TransactionList transactions={transactions} />
                )}
                <div className="pagination">
                  <button
                    className="secondary"
                    disabled={page === 0 || loading}
                    onClick={() => {
                      setLoading(true);
                      setPage(page - 1);
                    }}
                  >
                    ← Previous
                  </button>
                  <span>Page {page + 1}</span>
                  <button
                    className="secondary"
                    disabled={transactions.length < 10 || loading}
                    onClick={() => {
                      setLoading(true);
                      setPage(page + 1);
                    }}
                  >
                    Next →
                  </button>
                </div>
              </section>
            </>
          )}
          <footer className="dashboard-footer">
            <span>Made for your everyday.</span>
            <strong>FuroCash · Demo</strong>
          </footer>
        </main>
      </div>
      {nav(true)}
    </div>
  );
}
