export interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

interface ProblemDetails {
  detail?: string;
  correlationId?: string;
}

interface RequestOptions {
  accessToken: string;
  method?: "GET" | "POST";
  body?: unknown;
  idempotencyKey?: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly correlationId?: string;

  constructor(status: number, message: string, correlationId?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.correlationId = correlationId;
  }
}

export async function requestJson<T>(
  baseUrl: string,
  path: string,
  options: RequestOptions,
): Promise<T> {
  const correlationId = crypto.randomUUID();

  const headers = new Headers({
    Authorization: `Bearer ${options.accessToken}`,
    "X-Correlation-ID": correlationId,
  });

  if (options.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }

  if (options.idempotencyKey) {
    headers.set("Idempotency-Key", options.idempotencyKey);
  }

  const response = await fetch(`${baseUrl}${path}`, {
    method: options.method ?? "GET",
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  if (!response.ok) {
    let problem: ProblemDetails | undefined;

    try {
      problem = (await response.json()) as ProblemDetails;
    } catch {
      // The status still tells us enough when the response has no JSON body.
    }

    throw new ApiError(
      response.status,
      problem?.detail ?? `Request failed with HTTP ${response.status}`,
      problem?.correlationId ?? correlationId,
    );
  }

  return (await response.json()) as T;
}
