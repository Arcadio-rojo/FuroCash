import { useState } from "react";
import Auth from "./screens/Auth";
import { tokenStore } from "./api";
import type { User } from "./api";

export default function App() {
  const [user, setUser] = useState<User | null>(null);

  if (!user) {
    return <Auth onLoggedIn={setUser} />;
  }

  return (
      <main className="screen">
        <h1>Welcome, {user.fullName}</h1>
        <p className="lede">Balance: {user.balance}</p>
        <button
            className="btn"
            onClick={() => {
              tokenStore.clear();
              setUser(null);
            }}
        >
          Log out
        </button>
      </main>
  );
}