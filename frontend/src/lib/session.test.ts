import { afterEach, describe, expect, it, vi } from "vitest";

describe("memory session", () => {
  afterEach(() => {
    vi.resetModules();
  });

  it("keeps the access token and customer ID in module memory", async () => {
    const { getAccessToken, getCustomerId, hasSession, setSession } =
      await import("@/lib/session");

    expect(hasSession()).toBe(false);

    setSession("access-token", "customer-1");

    expect(hasSession()).toBe(true);
    expect(getAccessToken()).toBe("access-token");
    expect(getCustomerId()).toBe("customer-1");
  });

  it("clears the in-memory session", async () => {
    const {
      clearSession,
      getAccessToken,
      getCustomerId,
      hasSession,
      setSession,
    } = await import("@/lib/session");

    setSession("access-token", "customer-1");

    clearSession();

    expect(hasSession()).toBe(false);

    expect(() => getAccessToken()).toThrow("No access token is available");

    expect(() => getCustomerId()).toThrow("No customer is selected");
  });
});
