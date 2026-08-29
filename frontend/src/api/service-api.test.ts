import { beforeEach, describe, expect, it, vi } from "vitest";

import { getAccount, getAccounts } from "@/api/account-api";
import { getCustomer } from "@/api/customer-api";
import {
  createIdempotencyKey,
  deposit,
  getTransactionHistory,
  transfer,
  withdraw,
} from "@/api/transaction-api";

const mocks = vi.hoisted(() => ({
  requestJson: vi.fn(),
  getRuntimeConfig: vi.fn(),
}));

vi.mock("@/api/api-client", () => ({
  requestJson: mocks.requestJson,
}));

vi.mock("@/config/runtime-config", () => ({
  getRuntimeConfig: mocks.getRuntimeConfig,
}));

describe("service API clients", () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mocks.getRuntimeConfig.mockReturnValue({
      environment: "local",
      customerApiBaseUrl: "http://customer",
      accountApiBaseUrl: "http://account",
      transactionApiBaseUrl: "http://transaction",
    });
  });

  it("loads a customer from customer-service", async () => {
    await getCustomer("customer 1", "token");

    expect(mocks.requestJson).toHaveBeenCalledWith(
      "http://customer",
      "/api/v1/customers/customer%201",
      {
        accessToken: "token",
      },
    );
  });

  it("loads the customer account list", async () => {
    await getAccounts("customer 1", "token");

    expect(mocks.requestJson).toHaveBeenCalledWith(
      "http://account",
      "/api/v1/customers/customer%201/accounts?page=0&size=20",
      {
        accessToken: "token",
      },
    );
  });

  it("loads one account", async () => {
    await getAccount("account 1", "token");

    expect(mocks.requestJson).toHaveBeenCalledWith(
      "http://account",
      "/api/v1/accounts/account%201",
      {
        accessToken: "token",
      },
    );
  });

  it("creates a transfer", async () => {
    const request = {
      sourceAccountId: "account-1",
      destinationAccountId: "account-2",
      amount: 25,
      currency: "GBP",
    };

    await transfer(request, "token", "transfer-key");

    expect(mocks.requestJson).toHaveBeenCalledWith(
      "http://transaction",
      "/api/v1/transfers",
      {
        accessToken: "token",
        method: "POST",
        body: request,
        idempotencyKey: "transfer-key",
      },
    );
  });

  it("creates a deposit", async () => {
    const request = {
      destinationAccountId: "account-2",
      amount: 50,
      currency: "GBP",
    };

    await deposit(request, "token", "deposit-key");

    expect(mocks.requestJson).toHaveBeenCalledWith(
      "http://transaction",
      "/api/v1/deposits",
      {
        accessToken: "token",
        method: "POST",
        body: request,
        idempotencyKey: "deposit-key",
      },
    );
  });

  it("creates a withdrawal", async () => {
    const request = {
      sourceAccountId: "account-1",
      amount: 30,
      currency: "GBP",
    };

    await withdraw(request, "token", "withdrawal-key");

    expect(mocks.requestJson).toHaveBeenCalledWith(
      "http://transaction",
      "/api/v1/withdrawals",
      {
        accessToken: "token",
        method: "POST",
        body: request,
        idempotencyKey: "withdrawal-key",
      },
    );
  });

  it("loads account transaction history", async () => {
    await getTransactionHistory("account 1", "token");

    expect(mocks.requestJson).toHaveBeenCalledWith(
      "http://transaction",
      "/api/v1/accounts/account%201/transactions?page=0&size=20",
      {
        accessToken: "token",
      },
    );
  });

  it("creates an operation-specific idempotency key", () => {
    vi.stubGlobal("crypto", {
      randomUUID: vi.fn(() => "uuid-123"),
    });

    expect(createIdempotencyKey("TRANSFER")).toBe("transfer-uuid-123");

    vi.unstubAllGlobals();
  });
});
