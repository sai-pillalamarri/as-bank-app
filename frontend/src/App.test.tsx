import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import App from "@/App";

const mocks = vi.hoisted(() => ({
  useSession: vi.fn(),
  signOut: vi.fn(),
}));

vi.mock("@/config/runtime-config", () => ({
  getRuntimeConfig: () => ({
    environment: "local",
    customerApiBaseUrl: "http://customer",
    accountApiBaseUrl: "http://account",
    transactionApiBaseUrl: "http://transaction",
  }),
}));

vi.mock("@/lib/session-context", () => ({
  useSession: mocks.useSession,
}));

vi.mock("@/components/local-session-form", () => ({
  LocalSessionForm: () => <div>Local session form</div>,
}));

vi.mock("@/components/banking-dashboard", () => ({
  BankingDashboard: () => <div>Banking dashboard content</div>,
}));

describe("App", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows the local login screen without a session", () => {
    mocks.useSession.mockReturnValue({
      session: null,
      signIn: vi.fn(),
      signOut: mocks.signOut,
    });

    render(<App />);

    expect(screen.getByText("Local session form")).toBeInTheDocument();

    expect(screen.getByText("Personal banking")).toBeInTheDocument();

    expect(screen.getByText("Balances")).toBeInTheDocument();

    expect(screen.getByText("Transfers")).toBeInTheDocument();

    expect(screen.getByText("Ledger history")).toBeInTheDocument();
  });

  it("shows the banking dashboard for an authenticated session", async () => {
    const user = userEvent.setup();

    mocks.useSession.mockReturnValue({
      session: {
        accessToken: "access-token",
        customerId: "customer-1",
      },
      signIn: vi.fn(),
      signOut: mocks.signOut,
    });

    render(<App />);

    expect(screen.getByText("Banking dashboard content")).toBeInTheDocument();

    await user.click(
      screen.getByRole("button", {
        name: "Sign out",
      }),
    );

    expect(mocks.signOut).toHaveBeenCalledTimes(1);
  });
});
