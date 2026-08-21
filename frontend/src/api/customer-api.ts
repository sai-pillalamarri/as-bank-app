import { getRuntimeConfig } from "@/config/runtime-config";

export interface Customer {
  id: string;
  firstName: string;
  lastName: string;
  status: string;
}

interface ProblemDetails {
  detail?: string;
}

export async function getCustomer(
  customerId: string,
  accessToken: string,
): Promise<Customer> {
  const { apiBaseUrl } = getRuntimeConfig();

  const response = await fetch(
    `${apiBaseUrl}/api/v1/customers/${encodeURIComponent(customerId)}`,
    {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  );

  if (!response.ok) {
    let message = `Request failed with HTTP ${response.status}`;

    try {
      const problem = (await response.json()) as ProblemDetails;

      if (problem.detail) {
        message = problem.detail;
      }
    } catch {
      // Keep the HTTP status message when the response has no JSON body.
    }

    throw new Error(message);
  }

  return (await response.json()) as Customer;
}
