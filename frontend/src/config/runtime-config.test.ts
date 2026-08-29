import { afterEach, describe, expect, it, vi } from "vitest";

const config = {
  environment: "local",
  customerApiBaseUrl: "http://localhost:8080",
  accountApiBaseUrl: "http://localhost:8082",
  transactionApiBaseUrl: "http://localhost:8084",
};

describe("runtime config", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.resetModules();
  });

  it("loads config.json without using the browser cache", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: vi.fn().mockResolvedValue(config),
    });

    vi.stubGlobal("fetch", fetchMock);

    const { getRuntimeConfig, loadRuntimeConfig } =
      await import("@/config/runtime-config");

    await loadRuntimeConfig();

    expect(fetchMock).toHaveBeenCalledWith("/config.json", {
      cache: "no-store",
    });

    expect(getRuntimeConfig()).toEqual(config);
  });

  it("fails when config.json cannot be loaded", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 503,
      }),
    );

    const { loadRuntimeConfig } = await import("@/config/runtime-config");

    await expect(loadRuntimeConfig()).rejects.toThrow(
      "Unable to load configuration: HTTP 503",
    );
  });

  it("fails when configuration is read before startup loads it", async () => {
    const { getRuntimeConfig } = await import("@/config/runtime-config");

    expect(() => getRuntimeConfig()).toThrow(
      "Runtime configuration has not been loaded",
    );
  });
});
