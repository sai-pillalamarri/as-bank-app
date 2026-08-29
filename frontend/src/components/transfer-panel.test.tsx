import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { Account } from "@/api/account-api";
import { TransferPanel } from "@/components/transfer-panel";

const mocks = vi.hoisted(() => ({
  transfer: vi.fn(),
  createIdempotencyKey: vi.fn(),
  onCompleted: vi.fn(),
}));

vi.mock("@/api/transaction-api", () => ({
  transfer: mocks.transfer,
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
];

describe("TransferPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mocks.createIdempotencyKey.mockReturnValue("transfer-key-1");
  });

  it("reuses the idempotency key when the same transfer is retried", async () => {
    const user = userEvent.setup();

    mocks.transfer
      .mockRejectedValueOnce(new Error("Network request failed"))
      .mockResolvedValueOnce({
        id: "transaction-1",
        type: "TRANSFER",
        status: "APPLIED",
        failureReason: null,
        sourceAccountId: "account-1",
        destinationAccountId: "account-2",
        amount: 25,
        currency: "GBP",
        createdAt: "2026-08-29T10:00:00Z",
        completedAt: "2026-08-29T10:00:01Z",
      });

    render(
      <TransferPanel accounts={accounts} onCompleted={mocks.onCompleted} />,
    );

    await user.type(screen.getByLabelText("Amount"), "25");

    await user.click(
      screen.getByRole("button", {
        name: "Transfer",
      }),
    );

    expect(
      await screen.findByText("Network request failed"),
    ).toBeInTheDocument();

    expect(
      screen.getByRole("button", {
        name: "Retry transfer",
      }),
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole("button", {
        name: "Retry transfer",
      }),
    );

    expect(await screen.findByText("Transfer completed")).toBeInTheDocument();

    expect(mocks.createIdempotencyKey).toHaveBeenCalledTimes(1);

    expect(mocks.transfer).toHaveBeenCalledTimes(2);

    expect(mocks.transfer).toHaveBeenNthCalledWith(
      1,
      {
        sourceAccountId: "account-1",
        destinationAccountId: "account-2",
        amount: 25,
        currency: "GBP",
      },
      "access-token",
      "transfer-key-1",
    );

    expect(mocks.transfer).toHaveBeenNthCalledWith(
      2,
      {
        sourceAccountId: "account-1",
        destinationAccountId: "account-2",
        amount: 25,
        currency: "GBP",
      },
      "access-token",
      "transfer-key-1",
    );

    expect(mocks.onCompleted).toHaveBeenCalledTimes(1);
  });
});
