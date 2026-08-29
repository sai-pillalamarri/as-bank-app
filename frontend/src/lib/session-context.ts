import { createContext, useContext } from "react";

export interface Session {
  accessToken: string;
  customerId: string;
}

export interface SessionContextValue {
  session: Session | null;
  signIn: (accessToken: string, customerId: string) => void;
  signOut: () => void;
}

export const SessionContext = createContext<SessionContextValue | undefined>(
  undefined,
);

export function useSession(): SessionContextValue {
  const context = useContext(SessionContext);

  if (!context) {
    throw new Error("useSession must be used inside SessionProvider");
  }

  return context;
}
