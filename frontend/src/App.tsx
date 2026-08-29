import { BankingDashboard } from "@/components/banking-dashboard";
import { LocalSessionForm } from "@/components/local-session-form";
import { Button } from "@/components/ui/button";
import { getRuntimeConfig } from "@/config/runtime-config";
import { useSession } from "@/lib/session-context";

function App() {
  const { environment } = getRuntimeConfig();
  const { session, signOut } = useSession();

  if (!session) {
    return (
      <div className="min-h-screen bg-slate-50 text-slate-950">
        <header className="border-b border-white/10 bg-slate-950 text-white">
          <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-5">
            <div className="flex items-center gap-3">
              <div className="flex size-10 items-center justify-center rounded-xl bg-blue-600 font-bold">
                AS
              </div>

              <div>
                <p className="font-semibold">AS Bank</p>

                <p className="text-xs text-slate-400">Customer banking</p>
              </div>
            </div>

            <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-xs text-slate-300">
              {environment}
            </span>
          </div>
        </header>

        <main className="bg-gradient-to-br from-slate-950 via-blue-950 to-slate-900">
          <div className="mx-auto grid min-h-[calc(100vh-81px)] max-w-6xl gap-12 px-6 py-16 lg:grid-cols-[1.1fr_0.9fr] lg:items-center">
            <section className="text-white">
              <p className="text-sm font-medium uppercase tracking-[0.2em] text-blue-300">
                Personal banking
              </p>

              <h1 className="mt-4 max-w-xl text-4xl font-semibold tracking-tight sm:text-5xl">
                Accounts, transfers and transaction history in one place.
              </h1>

              <p className="mt-6 max-w-xl leading-7 text-slate-300">
                This local sign-in uses the mock OAuth issuer. Real Cognito
                Authorization Code with PKCE will replace it later in Stage 7.
              </p>

              <div className="mt-8 grid max-w-xl gap-3 sm:grid-cols-3">
                <Feature label="Accounts" value="Balances" />

                <Feature label="Payments" value="Transfers" />

                <Feature label="Activity" value="Ledger history" />
              </div>
            </section>

            <LocalSessionForm />
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50 text-slate-950">
      <header className="sticky top-0 z-20 border-b border-slate-800 bg-slate-950 text-white">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4">
          <div className="flex items-center gap-3">
            <div className="flex size-9 items-center justify-center rounded-lg bg-blue-600 font-bold">
              AS
            </div>

            <div>
              <p className="font-semibold">AS Bank</p>

              <p className="text-xs text-slate-400">{environment}</p>
            </div>
          </div>

          <Button
            type="button"
            variant="outline"
            onClick={signOut}
            className="border-slate-700 bg-transparent text-white hover:bg-slate-800 hover:text-white"
          >
            Sign out
          </Button>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-6 py-10">
        <BankingDashboard />
      </main>
    </div>
  );
}

function Feature({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-white/10 bg-white/5 p-4">
      <p className="text-xs text-slate-400">{label}</p>

      <p className="mt-1 font-medium">{value}</p>
    </div>
  );
}

export default App;
