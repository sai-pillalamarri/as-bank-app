import { useCallback, useMemo, useState, type ReactNode } from "react";

import { SessionContext, type Session } from "@/lib/session-context";
import {
  clearSession as clearMemorySession,
  setSession as setMemorySession,
} from "@/lib/session";

export function SessionProvider({ children }: { children: ReactNode }) {
  const [session, setSessionState] = useState<Session | null>(null);

  const signIn = useCallback((accessToken: string, customerId: string) => {
    const nextSession: Session = {
      accessToken,
      customerId,
    };

    setMemorySession(accessToken, customerId);

    setSessionState(nextSession);
  }, []);

  const signOut = useCallback(() => {
    clearMemorySession();
    setSessionState(null);
  }, []);

  const value = useMemo(
    () => ({
      session,
      signIn,
      signOut,
    }),
    [session, signIn, signOut],
  );

  return (
    <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
  );
}
