import { createContext, useContext } from "react";
import type { User, LoginResponse } from "../api";
interface Session {
  user: User | null;
  loading: boolean;
  error: string;
  notice: string;
  accept: (session: LoginResponse) => void;
  logout: () => void;
  retry: () => void;
  updateUser: (user: User) => void;
}
export const AuthContext = createContext<Session | null>(null);

export function useAuth() {
  const auth = useContext(AuthContext);
  if (!auth) throw new Error("AuthProvider is required");
  return auth;
}
