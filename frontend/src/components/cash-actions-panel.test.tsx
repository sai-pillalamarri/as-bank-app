import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { Account } from "@/api/account-api";
import { CashActionsPanel } from "@/components/cash-actions-panel";

const mocks = vi.hoisted(() => ({
  deposit: vi.fn(),
  withdraw: vi.fn(),
  createIdempotencyKey: vi.fn(),
  onCompleted: vi.fn(),
}));

vi.mock("@/api/transaction-api", () => ({
  deposit: mocks.deposit,
  withdraw: mocks.withdraw,
  createIdempotencyKey: mocks.createIdempotencyKey,
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

const accounts: Account[] = [
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
];

describe("CashActionsPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mocks.createIdempotencyKey.mockReturnValue("operation-key-1");
  });

  it("creates a deposit", async () => {
    const user = userEvent.setup();

    mocks.deposit.mockResolvedValue({
      id: "transaction-1",
      type: "DEPOSIT",
      status: "APPLIED",
      failureReason: null,
      sourceAccountId: null,
      destinationAccountId: "account-1",
      amount: 50,
      currency: "GBP",
      createdAt: "2026-08-29T10:00:00Z",
      completedAt: "2026-08-29T10:00:01Z",
    });

    render(
      <CashActionsPanel accounts={accounts} onCompleted={mocks.onCompleted} />,
    );

    await user.type(screen.getByLabelText("Amount"), "50");

    const depositButtons = screen.getAllByRole("button", {
      name: "Deposit",
    });

    await user.click(depositButtons[depositButtons.length - 1]);

    expect(await screen.findByText("Deposit completed")).toBeInTheDocument();

    expect(mocks.createIdempotencyKey).toHaveBeenCalledWith("DEPOSIT");

    expect(mocks.deposit).toHaveBeenCalledWith(
      {
        destinationAccountId: "account-1",
        amount: 50,
        currency: "GBP",
      },
      "access-token",
      "operation-key-1",
    );

    expect(mocks.onCompleted).toHaveBeenCalledTimes(1);
  });

  it("creates a withdrawal", async () => {
    const user = userEvent.setup();

    mocks.withdraw.mockResolvedValue({
      id: "transaction-2",
      type: "WITHDRAWAL",
      status: "APPLIED",
      failureReason: null,
      sourceAccountId: "account-1",
      destinationAccountId: null,
      amount: 30,
      currency: "GBP",
      createdAt: "2026-08-29T10:00:00Z",
      completedAt: "2026-08-29T10:00:01Z",
    });

    render(
      <CashActionsPanel accounts={accounts} onCompleted={mocks.onCompleted} />,
    );

    await user.click(
      screen.getByRole("button", {
        name: "Withdraw",
      }),
    );

    await user.type(screen.getByLabelText("Amount"), "30");

    const withdrawButtons = screen.getAllByRole("button", {
      name: "Withdraw",
    });

    await user.click(withdrawButtons[withdrawButtons.length - 1]);

    expect(await screen.findByText("Withdrawal completed")).toBeInTheDocument();

    expect(mocks.createIdempotencyKey).toHaveBeenCalledWith("WITHDRAWAL");

    expect(mocks.withdraw).toHaveBeenCalledWith(
      {
        sourceAccountId: "account-1",
        amount: 30,
        currency: "GBP",
      },
      "access-token",
      "operation-key-1",
    );

    expect(mocks.onCompleted).toHaveBeenCalledTimes(1);
  });
});
