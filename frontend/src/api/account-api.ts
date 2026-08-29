import { requestJson, type PagedResponse } from "@/api/api-client";
import { getRuntimeConfig } from "@/config/runtime-config";

export type AccountType = "CURRENT" | "SAVINGS";

export type AccountStatus = "ACTIVE" | "FROZEN" | "CLOSED";

export interface Account {
  id: string;
  customerId: string;
  accountNumber: string;
  type: AccountType;
  status: AccountStatus;
  balance: number;
  currency: string;
  version: number;
}

export function getAccounts(
  customerId: string,
  accessToken: string,
): Promise<PagedResponse<Account>> {
  const { accountApiBaseUrl } = getRuntimeConfig();

  return requestJson<PagedResponse<Account>>(
    accountApiBaseUrl,
    `/api/v1/customers/${encodeURIComponent(customerId)}/accounts?page=0&size=20`,
    {
      accessToken,
    },
  );
}

export function getAccount(
  accountId: string,
  accessToken: string,
): Promise<Account> {
  const { accountApiBaseUrl } = getRuntimeConfig();

  return requestJson<Account>(
    accountApiBaseUrl,
    `/api/v1/accounts/${encodeURIComponent(accountId)}`,
    {
      accessToken,
    },
  );
}
