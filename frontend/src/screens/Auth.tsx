import { useState } from "react";
import type { FormEvent } from "react";
import { api, tokenStore } from "../api";
import type { User } from "../api";

interface Props {
    onLoggedIn: (user: User) => void;
}

export default function Auth({ onLoggedIn }: Props) {
    const [mode, setMode] = useState<"login" | "register">("login");
    const [mobile, setMobile] = useState("");
    const [pin, setPin] = useState("");
    const [fullName, setFullName] = useState("");
    const [error, setError] = useState("");
    const [busy, setBusy] = useState(false);

    async function handleSubmit(e: FormEvent) {
        e.preventDefault();
        setError("");
        setBusy(true);
        try {
            if (mode === "register") {
                await api.register(mobile, pin, fullName);
            }
            const res = await api.login(mobile, pin);
            tokenStore.set(res.token);
            onLoggedIn(res.user);
        } catch (err) {
            setError(err instanceof Error ? err.message : "Something went wrong");
        } finally {
            setBusy(false);
        }
    }

    const isRegister = mode === "register";

    return (
        <main className="screen">
            <h1>FuroCash</h1>
            <p className="lede">
                {isRegister
                    ? "Create an account with your mobile number."
                    : "Log in to send and receive money."}
            </p>

            <form onSubmit={handleSubmit}>
                {isRegister && (
                    <div className="field">
                        <label htmlFor="fullName">Full name</label>
                        <input
                            id="fullName"
                            value={fullName}
                            onChange={(e) => setFullName(e.target.value)}
                            autoComplete="name"
                            required
                        />
                    </div>
                )}

                <div className="field">
                    <label htmlFor="mobile">Mobile number</label>
                    <input
                        id="mobile"
                        value={mobile}
                        onChange={(e) => setMobile(e.target.value.replace(/\D/g, ""))}
                        inputMode="tel"
                        maxLength={11}
                        placeholder="09171234567"
                        autoComplete="tel"
                        required
                    />
                </div>

                <div className="field">
                    <label htmlFor="pin">4-digit PIN</label>
                    <input
                        id="pin"
                        type="password"
                        value={pin}
                        onChange={(e) => setPin(e.target.value.replace(/\D/g, ""))}
                        inputMode="numeric"
                        maxLength={4}
                        autoComplete={isRegister ? "new-password" : "current-password"}
                        required
                    />
                </div>

                {error && <p className="error" role="alert">{error}</p>}

                <button className="btn" type="submit" disabled={busy}>
                    {busy ? "Please wait..." : isRegister ? "Create account" : "Log in"}
                </button>
            </form>

            <p className="switch">
                {isRegister ? "Already have an account? " : "New to FuroCash? "}
                <button
                    className="link"
                    type="button"
                    onClick={() => {
                        setMode(isRegister ? "login" : "register");
                        setError("");
                    }}
                >
                    {isRegister ? "Log in" : "Create an account"}
                </button>
            </p>
        </main>
    );
}