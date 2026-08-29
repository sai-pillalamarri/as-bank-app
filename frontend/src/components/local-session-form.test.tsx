import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { LocalSessionForm } from "@/components/local-session-form";

const mocks = vi.hoisted(() => ({
  getCustomer: vi.fn(),
  signIn: vi.fn(),
}));

vi.mock("@/api/customer-api", () => ({
  getCustomer: mocks.getCustomer,
}));

vi.mock("@/lib/session-context", () => ({
  useSession: () => ({
    session: null,
    signIn: mocks.signIn,
    signOut: vi.fn(),
  }),
}));

describe("LocalSessionForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("starts a session after validating the token", async () => {
    const user = userEvent.setup();

    mocks.getCustomer.mockResolvedValue({
      id: "11111111-1111-1111-1111-111111111111",
      firstName: "Alex",
      lastName: "Morgan",
      status: "ACTIVE",
    });

    render(<LocalSessionForm />);

    await user.type(screen.getByLabelText("Access token"), "access-token");

    await user.click(
      screen.getByRole("button", {
        name: "Continue",
      }),
    );

    expect(mocks.getCustomer).toHaveBeenCalledWith(
      "11111111-1111-1111-1111-111111111111",
      "access-token",
    );

    expect(mocks.signIn).toHaveBeenCalledWith(
      "access-token",
      "11111111-1111-1111-1111-111111111111",
    );
  });

  it("shows an authentication error", async () => {
    const user = userEvent.setup();

    mocks.getCustomer.mockRejectedValue(new Error("Invalid access token"));

    render(<LocalSessionForm />);

    await user.type(screen.getByLabelText("Access token"), "bad-token");

    await user.click(
      screen.getByRole("button", {
        name: "Continue",
      }),
    );

    expect(await screen.findByText("Sign in failed")).toBeInTheDocument();

    expect(screen.getByText("Invalid access token")).toBeInTheDocument();

    expect(mocks.signIn).not.toHaveBeenCalled();
  });
});
