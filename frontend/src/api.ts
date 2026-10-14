const API_BASE = "http://localhost:7070";
const TOKEN_KEY = "furocash_token";

export interface User{
    id: number;
    mobileNumber: string;
    fullName: string;
    balance: number;
}

export interface LoginResponse {
    token: string;
    user: User;
}

export interface Transaction {
    id: number;
    type: "CASH_IN" | "TRANSFER_OUT" | "TRANSFER_IN";
    amount: number;
    details: string;
    referenceNo: string;
    balanceAfter: number;
    createdAt: string;
}

export const tokenStore = {
    get: () => localStorage.getItem(TOKEN_KEY),
    set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
    clear: () => localStorage.removeItem(TOKEN_KEY),
};

export class ApiError extends Error {
    status: number;
    constructor(message: string, status: number) {
        super(message);
        this.status = status;
    }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
    const headers: Record<string, string> = { "Content-Type": "application/json" };
    const token = tokenStore.get();
    if (token) {
        headers.Authorization = `Bearer ${token}`;
    }

    const res = await fetch(`${API_BASE}${path}`, { ...options, headers});
    const text = await res.text();
    const data = text ? JSON.parse(text) : null;

    if (!res.ok) {
        throw new ApiError(data?.error ?? "Something went wrong", res.status);
    }
    return data as T;
}

export const api =  {
    health: () => request<{ status: string }>("/api/health"),

    register: (mobile: string, pin: string, fullName: string) =>
        request<{ id: number }>("/api/auth/register", {
            method: "POST",
            body: JSON.stringify({ mobile, pin, fullName}),
        }),

    login:(mobile: string, pin: string) =>
        request<LoginResponse>("/api/auth/login", {
            method: "POST",
            body: JSON.stringify({mobile, pin}),
        }),

    balance: () => request<{ balance: number }>("/api/account/balance"),

    cashIn: (amount: number) =>
        request<{ balance: number }>("/api/account/cash-in", {
            method: "POST",
            body: JSON.stringify({ amount }),
        }),

    transfer: (receiverMobile: string, amount: number)=>
        request<{ balance: number }>("/api/transfers", {
            method: "POST",
            body: JSON.stringify({ receiverMobile, amount}),
        }),

    transactions: () => request<Transaction[]>("/api/transactions"),

};