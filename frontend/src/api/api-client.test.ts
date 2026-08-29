import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError, requestJson } from "@/api/api-client";

describe("requestJson", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);

    vi.stubGlobal("crypto", {
      randomUUID: vi.fn(() => "correlation-123"),
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  it("sends the bearer token and correlation ID", async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: vi.fn().mockResolvedValue({
        id: "customer-1",
      }),
    });

    const response = await requestJson<{ id: string }>(
      "http://localhost:8080",
      "/api/v1/customers/customer-1",
      {
        accessToken: "access-token",
      },
    );

    expect(response).toEqual({
      id: "customer-1",
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);

    const [url, request] = fetchMock.mock.calls[0] as [string, RequestInit];

    expect(url).toBe("http://localhost:8080/api/v1/customers/customer-1");

    expect(request.method).toBe("GET");

    const headers = request.headers as Headers;

    expect(headers.get("Authorization")).toBe("Bearer access-token");

    expect(headers.get("X-Correlation-ID")).toBe("correlation-123");

    expect(headers.has("Content-Type")).toBe(false);
  });

  it("sends JSON and the idempotency key for a write", async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: vi.fn().mockResolvedValue({
        id: "transaction-1",
        status: "APPLIED",
      }),
    });

    await requestJson("http://localhost:8084", "/api/v1/transfers", {
      accessToken: "access-token",
      method: "POST",
      idempotencyKey: "transfer-123",
      body: {
        sourceAccountId: "account-1",
        destinationAccountId: "account-2",
        amount: 25,
        currency: "GBP",
      },
    });

    const [, request] = fetchMock.mock.calls[0] as [string, RequestInit];

    const headers = request.headers as Headers;

    expect(headers.get("Authorization")).toBe("Bearer access-token");

    expect(headers.get("Content-Type")).toBe("application/json");

    expect(headers.get("Idempotency-Key")).toBe("transfer-123");

    expect(request.body).toBe(
      JSON.stringify({
        sourceAccountId: "account-1",
        destinationAccountId: "account-2",
        amount: 25,
        currency: "GBP",
      }),
    );
  });

  it("throws an ApiError using Problem Details", async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 409,
      json: vi.fn().mockResolvedValue({
        detail: "Idempotency key already used for another request",
        correlationId: "server-correlation-456",
      }),
    });

    try {
      await requestJson("http://localhost:8084", "/api/v1/transfers", {
        accessToken: "access-token",
      });

      throw new Error("Expected requestJson to fail");
    } catch (caught) {
      expect(caught).toBeInstanceOf(ApiError);

      const error = caught as ApiError;

      expect(error.status).toBe(409);
      expect(error.message).toBe(
        "Idempotency key already used for another request",
      );
      expect(error.correlationId).toBe("server-correlation-456");
    }
  });

  it("falls back to the HTTP status when no Problem Details body exists", async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 503,
      json: vi.fn().mockRejectedValue(new Error("Invalid JSON")),
    });

    try {
      await requestJson("http://localhost:8084", "/api/v1/transfers", {
        accessToken: "access-token",
      });

      throw new Error("Expected requestJson to fail");
    } catch (caught) {
      expect(caught).toBeInstanceOf(ApiError);

      const error = caught as ApiError;

      expect(error.status).toBe(503);
      expect(error.message).toBe("Request failed with HTTP 503");
      expect(error.correlationId).toBe("correlation-123");
    }
  });
});
