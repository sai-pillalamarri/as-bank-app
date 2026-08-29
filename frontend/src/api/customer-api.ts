import { requestJson } from "@/api/api-client";
import { getRuntimeConfig } from "@/config/runtime-config";

export interface Customer {
  id: string;
  firstName: string;
  lastName: string;
  status: string;
}

export function getCustomer(
  customerId: string,
  accessToken: string,
): Promise<Customer> {
  const { customerApiBaseUrl } = getRuntimeConfig();

  return requestJson<Customer>(
    customerApiBaseUrl,
    `/api/v1/customers/${encodeURIComponent(customerId)}`,
    {
      accessToken,
    },
  );
}
