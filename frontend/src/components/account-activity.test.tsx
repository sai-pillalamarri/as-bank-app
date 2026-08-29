import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/api/api-client";
import { AccountActivity } from "@/components/account-activity";

const mocks = vi.hoisted(() => ({
  getTransactionHistory: vi.fn(),
}));

vi.mock("@/api/transaction-api", () => ({
  getTransactionHistory: mocks.getTransactionHistory,
}));

vi.mock("@/lib/session-context", () => ({
  useSession: () => ({
    session: {
      accessToken: "access-token",
      customerId: "customer-1",
    },
    signIn: vi.fn(),
    signOut: vi.fn(),
  }),
}));

describe("AccountActivity", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows ledger activity", async () => {
    mocks.getTransactionHistory.mockResolvedValue({
      items: [
        {
          id: "ledger-1",
          transactionId: "transaction-12345678",
          accountId: "account-1",
          direction: "DEBIT",
          amount: 25,
          currency: "GBP",
          createdAt: "2026-08-29T10:00:00Z",
        },
        {
          id: "ledger-2",
          transactionId: "transaction-87654321",
          accountId: "account-1",
          direction: "CREDIT",
          amount: 50,
          currency: "GBP",
          createdAt: "2026-08-29T11:00:00Z",
        },
      ],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
    });

    render(<AccountActivity accountId="account-1" />);

    expect(await screen.findByText("Money out")).toBeInTheDocument();

    expect(screen.getByText("Money in")).toBeInTheDocument();

    expect(screen.getByText("−£25.00")).toBeInTheDocument();

    expect(screen.getByText("+£50.00")).toBeInTheDocument();

    expect(mocks.getTransactionHistory).toHaveBeenCalledWith(
      "account-1",
      "access-token",
    );
  });

  it("shows API errors with the correlation ID", async () => {
    mocks.getTransactionHistory.mockRejectedValue(
      new ApiError(503, "Transaction service unavailable", "correlation-123"),
    );

    render(<AccountActivity accountId="account-1" />);

    expect(
      await screen.findByText("Unable to load activity"),
    ).toBeInTheDocument();

    expect(
      screen.getByText("Transaction service unavailable"),
    ).toBeInTheDocument();

    expect(
      screen.getByText("Correlation ID: correlation-123"),
    ).toBeInTheDocument();
  });
});
