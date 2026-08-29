import { requestJson, type PagedResponse } from "@/api/api-client";
import { getRuntimeConfig } from "@/config/runtime-config";

export type TransactionType = "TRANSFER" | "DEPOSIT" | "WITHDRAWAL";

export type TransactionStatus = "PENDING" | "APPLIED" | "REJECTED";

export type TransactionFailureReason =
  | "ACCOUNT_NOT_FOUND"
  | "ACCOUNT_FROZEN"
  | "ACCOUNT_CLOSED"
  | "INSUFFICIENT_FUNDS"
  | "CURRENCY_MISMATCH"
  | "SAME_ACCOUNT"
  | "INVALID_ACCOUNT_SELECTION"
  | "IDEMPOTENCY_CONFLICT";

export type LedgerDirection = "DEBIT" | "CREDIT";

export interface Transaction {
  id: string;
  type: TransactionType;
  status: TransactionStatus;
  failureReason: TransactionFailureReason | null;
  sourceAccountId: string | null;
  destinationAccountId: string | null;
  amount: number;
  currency: string;
  createdAt: string;
  completedAt: string | null;
}

export interface LedgerEntry {
  id: string;
  transactionId: string;
  accountId: string;
  direction: LedgerDirection;
  amount: number;
  currency: string;
  createdAt: string;
}

export interface TransferRequest {
  sourceAccountId: string;
  destinationAccountId: string;
  amount: number;
  currency: string;
}

export interface DepositRequest {
  destinationAccountId: string;
  amount: number;
  currency: string;
}

export interface WithdrawalRequest {
  sourceAccountId: string;
  amount: number;
  currency: string;
}

export function createIdempotencyKey(operation: TransactionType): string {
  return `${operation.toLowerCase()}-${crypto.randomUUID()}`;
}

export function transfer(
  request: TransferRequest,
  accessToken: string,
  idempotencyKey: string,
): Promise<Transaction> {
  const { transactionApiBaseUrl } = getRuntimeConfig();

  return requestJson<Transaction>(transactionApiBaseUrl, "/api/v1/transfers", {
    accessToken,
    method: "POST",
    body: request,
    idempotencyKey,
  });
}

export function deposit(
  request: DepositRequest,
  accessToken: string,
  idempotencyKey: string,
): Promise<Transaction> {
  const { transactionApiBaseUrl } = getRuntimeConfig();

  return requestJson<Transaction>(transactionApiBaseUrl, "/api/v1/deposits", {
    accessToken,
    method: "POST",
    body: request,
    idempotencyKey,
  });
}

export function withdraw(
  request: WithdrawalRequest,
  accessToken: string,
  idempotencyKey: string,
): Promise<Transaction> {
  const { transactionApiBaseUrl } = getRuntimeConfig();

  return requestJson<Transaction>(
    transactionApiBaseUrl,
    "/api/v1/withdrawals",
    {
      accessToken,
      method: "POST",
      body: request,
      idempotencyKey,
    },
  );
}

export function getTransactionHistory(
  accountId: string,
  accessToken: string,
): Promise<PagedResponse<LedgerEntry>> {
  const { transactionApiBaseUrl } = getRuntimeConfig();

  return requestJson<PagedResponse<LedgerEntry>>(
    transactionApiBaseUrl,
    `/api/v1/accounts/${encodeURIComponent(accountId)}/transactions?page=0&size=20`,
    {
      accessToken,
    },
  );
}
