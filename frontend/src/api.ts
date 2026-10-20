const API_BASE = import.meta.env?.VITE_API_BASE_URL ?? "";
const TOKEN_KEY = "furocash_token";
export interface User {
  id: number;
  mobileNumber: string;
  fullName: string;
  balance: number;
  accountIdentifier: string;
  email: string | null;
  createdAt: string | null;
}
export interface LoginResponse {
  token: string;
  user: User;
}
export interface Transaction {
  id: number;
  type: "CASH_IN" | "TRANSFER_OUT" | "TRANSFER_IN" | "CARD_PURCHASE";
  status: "COMPLETED";
  cardId?: string | null;
  receiptAvailable?: boolean;
  amount: number;
  details: string;
  referenceNo: string;
  balanceAfter: number;
  createdAt: string;
}
export interface Health {
  status: string;
  demoCashIn: boolean;
  smsMode: string;
}
export interface Receipt {
  reference: string;
  kind: string;
  status: "PENDING" | "COMPLETED";
  amount: number;
  balance: number;
  accountIdentifier: string;
  counterparty: string | null;
  note: string | null;
  createdAt: string;
  fee?: number | null;
  total?: number | null;
  method?: string | null;
  senderAccount?: string | null;
  senderName?: string | null;
  recipientAccount?: string | null;
  recipientName?: string | null;
}
export interface DepositOptions {
  enabled: boolean;
  minAmount: number;
  manualMaxAmount: number;
  fee: number;
  methods: { value: string; label: string }[];
}
export interface TransferReview {
  reference: string;
  recipient: Recipient;
  amount: number;
  fee: number;
  total: number;
  balance: number;
  note: string;
  qrReference: string | null;
  expiresAt: string;
}
export interface OperationStatus {
  state: "COMPLETED" | "PROCESSING" | "NOT_FOUND";
  receipt: Receipt | null;
}
export interface Recipient {
  accountIdentifier: string;
  fullName: string;
  mobileNumber: string;
}
export interface QrPayload {
  app: "FUROCASH";
  version: 1;
  purpose: "TRANSFER" | "DEPOSIT";
  accountIdentifier: string;
  reference: string;
  amount: string | null;
}
export interface ResolvedQr {
  recipient: Recipient;
  amount: string | null;
  reference: string;
  purpose: "TRANSFER" | "DEPOSIT";
}
export interface DemoCard {
  id: string;
  identifier: string;
  maskedIdentifier: string;
  cardholderName: string;
  status: "ACTIVE" | "FROZEN";
  dailyLimit: number;
  spentToday: number;
  expiresOn: string;
  createdAt: string;
}
export interface TransferInput {
  receiverAccount?: string;
  receiverMobile?: string;
  amount: string;
  note?: string;
  qrReference?: string;
  reviewReference?: string;
}
export const operationKey = () => crypto.randomUUID();
export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};
export class ApiError extends Error {
  status: number;
  outcomeUnknown: boolean;
  constructor(message: string, status: number, outcomeUnknown = false) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.outcomeUnknown = outcomeUnknown;
  }
}
const requestListeners = new Set<() => void>();
let pendingRequests = 0;
export const getPendingRequests = () => pendingRequests;
export function subscribeRequests(listener: () => void) {
  requestListeners.add(listener);
  return () => {
    requestListeners.delete(listener);
  };
}
async function request<T>(
  path: string,
  options: RequestInit = {},
  authenticated = true,
): Promise<T> {
  pendingRequests++;
  requestListeners.forEach((listener) => listener());
  try {
    return await performRequest<T>(path, options, authenticated);
  } finally {
    pendingRequests--;
    requestListeners.forEach((listener) => listener());
  }
}
async function performRequest<T>(
  path: string,
  options: RequestInit = {},
  authenticated = true,
): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...Object.fromEntries(new Headers(options.headers).entries()),
  };
  const token = authenticated ? tokenStore.get() : null;
  if (token) headers.Authorization = `Bearer ${token}`;
  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, { ...options, headers });
  } catch (error) {
    if (error instanceof Error && error.name === "AbortError") throw error;
    throw new ApiError(
      "Cannot reach FuroCash. Check your connection and that the Java server is running.",
      0,
      true,
    );
  }
  let text: string;
  try {
    text = await response.text();
  } catch (error) {
    if (error instanceof Error && error.name === "AbortError") throw error;
    throw new ApiError(
      "The response was interrupted. Check your account before trying again.",
      0,
      true,
    );
  }
  let data: unknown = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    /* A proxy may return HTML. */
  }
  if (!response.ok) {
    if (response.status === 401 && token && tokenStore.get() === token) {
      tokenStore.clear();
      window.dispatchEvent(new Event("furocash:session-expired"));
    }
    const message =
      data &&
      typeof data === "object" &&
      "error" in data &&
      typeof data.error === "string"
        ? data.error
        : `Request failed (${response.status}). Please try again later.`;
    throw new ApiError(message, response.status);
  }
  if (data === null && text !== "null" && response.status !== 204)
    throw new ApiError(
      "The server returned an unexpected response.",
      response.status,
      true,
    );
  return data as T;
}
const post = <T>(
  path: string,
  body: unknown,
  authenticated = false,
  key?: string,
  signal?: AbortSignal,
) =>
  request<T>(
    path,
    {
      method: "POST",
      body: JSON.stringify(body),
      headers: key ? { "Idempotency-Key": key } : {},
      signal,
    },
    authenticated,
  );
export const api = {
  health: () => request<Health>("/api/health", {}, false),
  requestRegistrationCode: (mobile: string, pin: string, fullName: string) =>
    post<{ message: string }>("/api/auth/register/request-code", {
      mobile,
      pin,
      fullName,
    }),
  register: (mobile: string, pin: string, fullName: string, otp: string) =>
    post<LoginResponse>("/api/auth/register", { mobile, pin, fullName, otp }),
  login: (mobile: string, pin: string) =>
    post<LoginResponse>("/api/auth/login", { mobile, pin }),
  me: (signal?: AbortSignal) => request<User>("/api/auth/me", { signal }),
  forgotPin: (mobile: string) =>
    post<{ message: string }>("/api/auth/forgot-pin", { mobile }),
  resetPin: (mobile: string, otp: string, pin: string) =>
    post<{ message: string }>("/api/auth/reset-pin", { mobile, otp, pin }),
  balance: () => request<{ balance: number }>("/api/account/balance"),
  cashIn: (amount: string, key = operationKey()) =>
    post<{ balance: number }>("/api/account/cash-in", { amount }, true, key),
  transfer: (receiverMobile: string, amount: string, key = operationKey()) =>
    post<Receipt>("/api/transfers", { receiverMobile, amount }, true, key),
  transactions: (page = 0, size = 10) =>
    request<Transaction[]>(`/api/transactions?page=${page}&size=${size}`),
  transactionReceipt: (reference: string) =>
    request<Receipt>(
      `/api/transactions/${encodeURIComponent(reference)}/receipt`,
    ),
  operationStatus: (key: string, signal?: AbortSignal) =>
    request<OperationStatus>(`/api/operations/${encodeURIComponent(key)}`, {
      signal,
    }),
  depositsOptions: () => request<DepositOptions>("/api/deposits/options"),
  prepareManualDeposit: (
    input: { amount: string; method: string; note: string },
    key: string,
  ) => post<Receipt>("/api/deposits/demo", input, true, key),
  reviewTransfer: (input: TransferInput) =>
    post<TransferReview>("/api/transfers/review", input, true),
  prepareDeposit: (amount: string, key: string) =>
    post<Receipt>(
      "/api/deposits/demo",
      { amount, method: "GCASH_DEMO" },
      true,
      key,
    ),
  completeDeposit: (reference: string, key: string, signal?: AbortSignal) =>
    post<Receipt>(
      `/api/deposits/demo/${encodeURIComponent(reference)}/complete`,
      {},
      true,
      key,
      signal,
    ),
  recipient: (identifier: string) =>
    post<Recipient>(
      "/api/transfers/validate-recipient",
      identifier.startsWith("FC-")
        ? { accountIdentifier: identifier }
        : { receiverMobile: identifier },
      true,
    ),
  send: (input: TransferInput, key: string, signal?: AbortSignal) =>
    post<Receipt>("/api/transfers", input, true, key, signal),
  createQr: (purpose: QrPayload["purpose"], amount: string | null) =>
    post<QrPayload>("/api/qr/requests", { purpose, amount }, true),
  resolveQr: (payload: QrPayload) =>
    post<ResolvedQr>("/api/qr/resolve", { payload }, true),
  profile: () => request<User>("/api/users/me"),
  updateProfile: (fullName: string, email: string | null) =>
    request<User>("/api/users/me", {
      method: "PATCH",
      body: JSON.stringify({ fullName, email }),
    }),
  card: () => request<DemoCard | null>("/api/cards/my"),
  createCard: () => post<DemoCard>("/api/cards", {}, true),
  cardStatus: (id: string, action: "freeze" | "unfreeze") =>
    request<DemoCard>(`/api/cards/${id}/${action}`, {
      method: "PATCH",
      body: "{}",
    }),
  cardLimit: (id: string, dailyLimit: string) =>
    request<DemoCard>(`/api/cards/${id}/limit`, {
      method: "PATCH",
      body: JSON.stringify({ dailyLimit }),
    }),
  cardTransactions: (id: string) =>
    request<Transaction[]>(`/api/cards/${id}/transactions`),
  purchase: (id: string, amount: string, merchant: string, key: string) =>
    post<Receipt>(
      `/api/cards/${id}/simulate-purchase`,
      { amount, merchant },
      true,
      key,
    ),
};
