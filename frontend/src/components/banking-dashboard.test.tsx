import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/api/api-client";
import { BankingDashboard } from "@/components/banking-dashboard";

const mocks = vi.hoisted(() => ({
  getCustomer: vi.fn(),
  getAccounts: vi.fn(),
  session: {
    accessToken: "access-token",
    customerId: "customer-1",
  },
}));

vi.mock("@/api/customer-api", () => ({
  getCustomer: mocks.getCustomer,
}));

vi.mock("@/api/account-api", () => ({
  getAccounts: mocks.getAccounts,
}));

vi.mock("@/lib/session-context", () => ({
  useSession: () => ({
    session: mocks.session,
    signIn: vi.fn(),
    signOut: vi.fn(),
  }),
}));

vi.mock("@/components/account-activity", () => ({
  AccountActivity: ({ accountId }: { accountId: string }) => (
    <div>Activity {accountId}</div>
  ),
}));

vi.mock("@/components/transfer-panel", () => ({
  TransferPanel: ({ onCompleted }: { onCompleted: () => void }) => (
    <button type="button" onClick={onCompleted}>
      Complete transfer
    </button>
  ),
}));

vi.mock("@/components/cash-actions-panel", () => ({
  CashActionsPanel: ({ onCompleted }: { onCompleted: () => void }) => (
    <button type="button" onClick={onCompleted}>
      Complete cash action
    </button>
  ),
}));

describe("BankingDashboard", () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mocks.getCustomer.mockResolvedValue({
      id: "customer-1",
      firstName: "Alex",
      lastName: "Morgan",
      status: "ACTIVE",
    });

    mocks.getAccounts.mockResolvedValue({
      items: [
        {
          id: "account-1",
          customerId: "customer-1",
          accountNumber: "10000001",
          type: "CURRENT",
          status: "ACTIVE",
          balance: 1000,
          currency: "GBP",
          version: 0,
        },
        {
          id: "account-2",
          customerId: "customer-1",
          accountNumber: "10000002",
          type: "SAVINGS",
          status: "ACTIVE",
          balance: 2500,
          currency: "GBP",
          version: 0,
        },
      ],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
    });
  });

  it("loads the customer and account dashboard", async () => {
    render(<BankingDashboard />);

    expect(await screen.findByText("Welcome back, Alex")).toBeInTheDocument();

    expect(screen.getByText("£1,000.00")).toBeInTheDocument();

    expect(screen.getByText("£2,500.00")).toBeInTheDocument();

    expect(screen.getByText("10000001")).toBeInTheDocument();

    expect(screen.getByText("10000002")).toBeInTheDocument();

    expect(mocks.getCustomer).toHaveBeenCalledWith(
      "customer-1",
      "access-token",
    );

    expect(mocks.getAccounts).toHaveBeenCalledWith(
      "customer-1",
      "access-token",
    );
  });

  it("reloads balances after a completed operation", async () => {
    const user = userEvent.setup();

    render(<BankingDashboard />);

    await screen.findByText("Welcome back, Alex");

    expect(mocks.getAccounts).toHaveBeenCalledTimes(1);

    await user.click(
      screen.getByRole("button", {
        name: "Complete transfer",
      }),
    );

    await waitFor(() => {
      expect(mocks.getAccounts).toHaveBeenCalledTimes(2);
    });

    expect(mocks.getCustomer).toHaveBeenCalledTimes(2);
  });

  it("shows API failures with a correlation ID", async () => {
    mocks.getCustomer.mockRejectedValue(
      new ApiError(503, "Customer service unavailable", "correlation-456"),
    );

    render(<BankingDashboard />);

    expect(
      await screen.findByText("Unable to load your accounts"),
    ).toBeInTheDocument();

    expect(
      screen.getByText("Customer service unavailable"),
    ).toBeInTheDocument();

    expect(
      screen.getByText("Correlation ID: correlation-456"),
    ).toBeInTheDocument();
  });
});
