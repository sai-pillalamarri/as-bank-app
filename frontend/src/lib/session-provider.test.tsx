import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useSession } from "@/lib/session-context";
import { SessionProvider } from "@/lib/session-provider";

const mocks = vi.hoisted(() => ({
  setMemorySession: vi.fn(),
  clearMemorySession: vi.fn(),
}));

vi.mock("@/lib/session", () => ({
  setSession: mocks.setMemorySession,
  clearSession: mocks.clearMemorySession,
}));

function SessionProbe() {
  const { session, signIn, signOut } = useSession();

  return (
    <div>
      <p>{session ? session.customerId : "signed-out"}</p>

      <button
        type="button"
        onClick={() => signIn("access-token", "customer-1")}
      >
        Sign in
      </button>

      <button type="button" onClick={signOut}>
        Sign out
      </button>
    </div>
  );
}

describe("SessionProvider", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("keeps React session state synchronized with memory storage", async () => {
    const user = userEvent.setup();

    render(
      <SessionProvider>
        <SessionProbe />
      </SessionProvider>,
    );

    expect(screen.getByText("signed-out")).toBeInTheDocument();

    await user.click(
      screen.getByRole("button", {
        name: "Sign in",
      }),
    );

    expect(screen.getByText("customer-1")).toBeInTheDocument();

    expect(mocks.setMemorySession).toHaveBeenCalledWith(
      "access-token",
      "customer-1",
    );

    await user.click(
      screen.getByRole("button", {
        name: "Sign out",
      }),
    );

    expect(screen.getByText("signed-out")).toBeInTheDocument();

    expect(mocks.clearMemorySession).toHaveBeenCalledTimes(1);
  });
});
