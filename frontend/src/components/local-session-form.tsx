import { useState, type FormEvent } from "react";

import { getCustomer } from "@/api/customer-api";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { useSession } from "@/lib/session-context";

const LOCAL_CUSTOMER_ID = "11111111-1111-1111-1111-111111111111";

export function LocalSessionForm() {
  const { signIn } = useSession();

  const [customerId, setCustomerId] = useState(LOCAL_CUSTOMER_ID);
  const [accessToken, setAccessToken] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const token = accessToken.trim();
    const id = customerId.trim();

    if (!token || !id) {
      return;
    }

    setLoading(true);
    setError("");

    try {
      await getCustomer(id, token);
      signIn(token, id);
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : "Unable to start the local session",
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <Card className="w-full max-w-md border-slate-200 shadow-xl">
      <CardHeader>
        <div className="mb-3 flex size-11 items-center justify-center rounded-xl bg-blue-50 font-semibold text-blue-700">
          AS
        </div>

        <CardTitle className="text-2xl">Local banking session</CardTitle>

        <CardDescription>
          Use the mock OAuth access token while the frontend is running locally.
        </CardDescription>
      </CardHeader>

      <CardContent>
        <form className="space-y-5" onSubmit={submit}>
          <div className="space-y-2">
            <label
              htmlFor="customerId"
              className="text-sm font-medium text-slate-700"
            >
              Customer ID
            </label>

            <input
              id="customerId"
              value={customerId}
              onChange={(event) => setCustomerId(event.target.value)}
              className="h-11 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm outline-none transition focus:border-blue-500 focus:bg-white focus:ring-4 focus:ring-blue-500/10"
            />
          </div>

          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label
                htmlFor="accessToken"
                className="text-sm font-medium text-slate-700"
              >
                Access token
              </label>

              <span className="text-xs text-slate-400">Memory only</span>
            </div>

            <textarea
              id="accessToken"
              value={accessToken}
              onChange={(event) => setAccessToken(event.target.value)}
              rows={5}
              autoComplete="off"
              placeholder="Paste OAuth2 access token"
              className="w-full resize-none rounded-lg border border-slate-200 bg-slate-50 px-3 py-3 text-sm outline-none transition focus:border-blue-500 focus:bg-white focus:ring-4 focus:ring-blue-500/10"
            />
          </div>

          {error && (
            <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3">
              <p className="text-sm font-medium text-red-900">Sign in failed</p>

              <p className="mt-1 text-sm text-red-700">{error}</p>
            </div>
          )}

          <Button
            type="submit"
            disabled={loading || !customerId.trim() || !accessToken.trim()}
            className="h-11 w-full bg-blue-600 text-white hover:bg-blue-700"
          >
            {loading ? "Checking session..." : "Continue"}
          </Button>

          <p className="text-center text-xs leading-5 text-slate-400">
            The token is not written to localStorage or sessionStorage.
          </p>
        </form>
      </CardContent>
    </Card>
  );
}
