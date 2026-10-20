import type { ReactNode } from "react";
import type { Receipt } from "../api";
import TransactionReceipt from "./TransactionReceipt";
export const menu = [
  { label: "Home", path: "/home", icon: "home" },
  { label: "Transactions", path: "/transactions", icon: "history" },
  { label: "Scan & Pay", path: "/scan", icon: "scan" },
  { label: "Card", path: "/card", icon: "card" },
  { label: "Me", path: "/me", icon: "user" },
];
const paths: Record<string, string> = {
  home: "m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1Z",
  history: "M4 7h16M16 3l4 4-4 4M20 17H4m4-4-4 4 4 4",
  scan: "M8 3H3v5m13-5h5v5M3 16v5h5m13-5v5h-5M7 7h3v3H7zm7 0h3v3h-3zM7 14h3v3H7zm7 0h3v3h-3z",
  card: "M3 5h18v14H3ZM3 10h18M7 15h3",
  user: "M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0ZM4 21v-2a8 8 0 0 1 16 0v2",
  arrow: "M5 19 19 5M5 5h14v14",
  plus: "M12 5v14M5 12h14",
  refresh:
    "M20 7v5h-5M4 17v-5h5M6.1 7a7 7 0 0 1 12-2L20 8M4 16l1.9 3a7 7 0 0 0 12-2",
  spark: "m12 2 3 7 7 3-7 3-3 7-3-7-7-3 7-3Z",
  check: "m5 12 4 4L19 6",
};
export function Icon({ name }: { name: string }) {
  return (
    <svg
      width="22"
      height="22"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name] ?? paths.arrow} />
    </svg>
  );
}
export function PageTitle({
  eyebrow,
  title,
  children,
}: {
  eyebrow: string;
  title: string;
  children?: ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        <span className="eyebrow">{eyebrow}</span>
        <h1>{title}</h1>
        {children && <p>{children}</p>}
      </div>
    </div>
  );
}
export function DemoNotice() {
  return <p className="demo-disclaimer">DEMO — NOT FOR REAL PAYMENTS</p>;
}
export function ErrorNotice({ error }: { error: string }) {
  return error ? (
    <p className="error" role="alert">
      {error}
    </p>
  ) : null;
}
export function errorMessage(error: unknown) {
  return error instanceof Error
    ? error.message
    : "Something went wrong. Please try again.";
}
export const validAmount = (value: string) =>
  /^\d+(\.\d{1,2})?$/.test(value) &&
  Number(value) > 0 &&
  Number(value) <= 999999999;
export const amountError =
  "Enter an amount from ₱0.01 to ₱999,999,999.00, with at most 2 decimals.";
export function PaymentReceipt({
  receipt,
  title,
}: {
  receipt: Receipt;
  title: string;
}) {
  return <TransactionReceipt receipt={receipt} title={title} />;
}
