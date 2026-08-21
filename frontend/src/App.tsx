import { useState, type FormEvent } from "react";

import { getCustomer, type Customer } from "@/api/customer-api";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { getRuntimeConfig } from "@/config/runtime-config";

const LOCAL_CUSTOMER_ID = "11111111-1111-1111-1111-111111111111";

function App() {
  const { environment } = getRuntimeConfig();

  const [customerId, setCustomerId] = useState(LOCAL_CUSTOMER_ID);
  const [accessToken, setAccessToken] = useState("");
  const [customer, setCustomer] = useState<Customer | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function loadCustomer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    setLoading(true);
    setCustomer(null);
    setError("");

    try {
      const result = await getCustomer(customerId.trim(), accessToken.trim());

      setCustomer(result);
    } catch (caught) {
      setError(
        caught instanceof Error ? caught.message : "Customer request failed",
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen bg-slate-50 text-slate-950">
      <header className="border-b border-white/10 bg-slate-950 text-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-5">
          <div className="flex items-center gap-3">
            <div className="flex size-10 items-center justify-center rounded-xl bg-blue-500 font-bold text-white">
              AS
            </div>

            <div>
              <p className="text-lg font-semibold leading-none">AS Bank</p>
              <p className="mt-1 text-xs text-slate-400">Customer Portal</p>
            </div>
          </div>

          <div className="flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-3 py-1.5">
            <span className="size-2 rounded-full bg-emerald-400" />
            <span className="text-xs font-medium text-slate-300">
              {environment}
            </span>
          </div>
        </div>
      </header>

      <main>
        <section className="bg-gradient-to-br from-slate-950 via-blue-950 to-slate-900 text-white">
          <div className="mx-auto grid max-w-6xl gap-10 px-6 py-16 lg:grid-cols-[1.15fr_0.85fr] lg:items-center lg:py-24">
            <div className="max-w-xl">
              <p className="mb-4 text-sm font-medium uppercase tracking-[0.2em] text-blue-300">
                Secure customer access
              </p>

              <h1 className="text-4xl font-semibold tracking-tight sm:text-5xl">
                Your customer profile,
                <span className="block text-blue-300">securely connected.</span>
              </h1>

              <p className="mt-6 max-w-lg text-base leading-7 text-slate-300">
                This Stage 1 portal connects the React frontend to the secured
                customer-service API using OAuth2 bearer-token authentication.
              </p>

              <div className="mt-8 grid max-w-lg grid-cols-3 gap-3">
                <div className="rounded-xl border border-white/10 bg-white/5 p-4">
                  <p className="text-xs text-slate-400">Frontend</p>
                  <p className="mt-1 font-medium">React 19</p>
                </div>

                <div className="rounded-xl border border-white/10 bg-white/5 p-4">
                  <p className="text-xs text-slate-400">Security</p>
                  <p className="mt-1 font-medium">OAuth2</p>
                </div>

                <div className="rounded-xl border border-white/10 bg-white/5 p-4">
                  <p className="text-xs text-slate-400">Backend</p>
                  <p className="mt-1 font-medium">Spring Boot</p>
                </div>
              </div>
            </div>

            <Card className="border-white/10 bg-white shadow-2xl">
              <CardHeader className="pb-4">
                <div className="mb-3 flex size-11 items-center justify-center rounded-xl bg-blue-50 text-lg font-semibold text-blue-700">
                  C
                </div>

                <CardTitle className="text-2xl">Customer lookup</CardTitle>

                <CardDescription>
                  Enter the local access token to retrieve the seeded customer
                  profile.
                </CardDescription>
              </CardHeader>

              <CardContent>
                <form className="space-y-5" onSubmit={loadCustomer}>
                  <div className="space-y-2">
                    <label
                      htmlFor="customerId"
                      className="text-sm font-medium text-slate-700"
                    >
                      Customer ID
                    </label>

                    <input
                      id="customerId"
                      className="h-11 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm outline-none transition focus:border-blue-500 focus:bg-white focus:ring-4 focus:ring-blue-500/10"
                      value={customerId}
                      onChange={(event) => setCustomerId(event.target.value)}
                    />
                  </div>

                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <label
                        htmlFor="accessToken"
                        className="text-sm font-medium text-slate-700"
                      >
                        Local access token
                      </label>

                      <span className="text-xs text-slate-400">
                        Memory only
                      </span>
                    </div>

                    <input
                      id="accessToken"
                      type="password"
                      autoComplete="off"
                      className="h-11 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm outline-none transition focus:border-blue-500 focus:bg-white focus:ring-4 focus:ring-blue-500/10"
                      placeholder="Paste OAuth2 access token"
                      value={accessToken}
                      onChange={(event) => setAccessToken(event.target.value)}
                    />
                  </div>

                  <Button
                    type="submit"
                    className="h-11 w-full bg-blue-600 text-white hover:bg-blue-700"
                    disabled={
                      loading || !customerId.trim() || !accessToken.trim()
                    }
                  >
                    {loading ? "Loading customer..." : "View customer"}
                  </Button>

                  <p className="text-center text-xs leading-5 text-slate-400">
                    The access token is kept in browser memory and is not
                    persisted.
                  </p>
                </form>
              </CardContent>
            </Card>
          </div>
        </section>

        <section className="mx-auto max-w-6xl px-6 py-10">
          {customer && (
            <Card className="overflow-hidden border-slate-200 shadow-sm">
              <div className="h-1 bg-blue-600" />

              <CardHeader className="border-b border-slate-100 bg-white">
                <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
                  <div>
                    <p className="text-xs font-medium uppercase tracking-wider text-slate-400">
                      Customer profile
                    </p>

                    <CardTitle className="mt-2 text-2xl">
                      {customer.firstName} {customer.lastName}
                    </CardTitle>

                    <CardDescription className="mt-1">
                      {customer.id}
                    </CardDescription>
                  </div>

                  <span className="w-fit rounded-full bg-emerald-50 px-3 py-1.5 text-xs font-semibold text-emerald-700 ring-1 ring-inset ring-emerald-600/20">
                    {customer.status}
                  </span>
                </div>
              </CardHeader>

              <CardContent className="grid gap-4 bg-white pt-6 sm:grid-cols-3">
                <div className="rounded-xl bg-slate-50 p-4">
                  <p className="text-xs font-medium uppercase tracking-wide text-slate-400">
                    First name
                  </p>
                  <p className="mt-2 font-semibold">{customer.firstName}</p>
                </div>

                <div className="rounded-xl bg-slate-50 p-4">
                  <p className="text-xs font-medium uppercase tracking-wide text-slate-400">
                    Last name
                  </p>
                  <p className="mt-2 font-semibold">{customer.lastName}</p>
                </div>

                <div className="rounded-xl bg-slate-50 p-4">
                  <p className="text-xs font-medium uppercase tracking-wide text-slate-400">
                    Account status
                  </p>
                  <p className="mt-2 font-semibold">{customer.status}</p>
                </div>
              </CardContent>
            </Card>
          )}

          {error && (
            <div className="rounded-xl border border-red-200 bg-red-50 px-5 py-4">
              <p className="font-medium text-red-900">
                Customer request failed
              </p>
              <p className="mt-1 text-sm text-red-700">{error}</p>
            </div>
          )}

          {!customer && !error && (
            <div className="flex items-center justify-between rounded-xl border border-slate-200 bg-white px-5 py-4 text-sm text-slate-500">
              <span>Customer details will appear here.</span>
              <span className="font-medium text-slate-400">
                customer-service
              </span>
            </div>
          )}
        </section>
      </main>

      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl flex-col justify-between gap-2 px-6 py-6 text-xs text-slate-400 sm:flex-row">
          <span>AS Bank learning project — synthetic data only.</span>
          <span>Stage 1 · Customer Service</span>
        </div>
      </footer>
    </div>
  );
}

export default App;
