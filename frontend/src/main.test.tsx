import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  loadRuntimeConfig: vi.fn(),
  render: vi.fn(),
  createRoot: vi.fn(),
}));

vi.mock("react-dom/client", () => ({
  createRoot: mocks.createRoot,
}));

vi.mock("@/config/runtime-config", () => ({
  loadRuntimeConfig: mocks.loadRuntimeConfig,
}));

vi.mock("./App", () => ({
  default: () => null,
}));

describe("frontend startup", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.clearAllMocks();

    document.body.innerHTML = '<div id="root"></div>';

    mocks.createRoot.mockReturnValue({
      render: mocks.render,
    });
  });

  it("loads runtime config before rendering React", async () => {
    mocks.loadRuntimeConfig.mockResolvedValue(undefined);

    await import("@/main");

    await vi.waitFor(() => {
      expect(mocks.loadRuntimeConfig).toHaveBeenCalledTimes(1);

      expect(mocks.createRoot).toHaveBeenCalledTimes(1);

      expect(mocks.render).toHaveBeenCalledTimes(1);
    });
  });

  it("shows a startup failure when runtime config cannot load", async () => {
    mocks.loadRuntimeConfig.mockRejectedValue(
      new Error("Unable to load configuration"),
    );

    await import("@/main");

    await vi.waitFor(() => {
      expect(document.getElementById("root")?.textContent).toBe(
        "Unable to load configuration",
      );
    });

    expect(mocks.createRoot).not.toHaveBeenCalled();
  });
});
