import { useEffect, useMemo, useState } from "react";

import { ApiError } from "@/api/api-client";
import { getAccounts, type Account } from "@/api/account-api";
import { getCustomer, type Customer } from "@/api/customer-api";
import { AccountActivity } from "@/components/account-activity";
import { CashActionsPanel } from "@/components/cash-actions-panel";
import { TransferPanel } from "@/components/transfer-panel";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useSession } from "@/lib/session-context";

export function BankingDashboard() {
  const { session } = useSession();

  const [customer, setCustomer] = useState<Customer | null>(null);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [correlationId, setCorrelationId] = useState("");
  const [refreshVersion, setRefreshVersion] = useState(0);

  useEffect(() => {
    if (!session) {
      return;
    }

    const customerId = session.customerId;
    const accessToken = session.accessToken;

    let cancelled = false;

    async function loadDashboard() {
      setLoading(true);
      setError("");
      setCorrelationId("");

      try {
        const [customerResponse, accountResponse] = await Promise.all([
          getCustomer(customerId, accessToken),
          getAccounts(customerId, accessToken),
        ]);

        if (cancelled) {
          return;
        }

        setCustomer(customerResponse);
        setAccounts(accountResponse.items);
      } catch (caught) {
        if (cancelled) {
          return;
        }

        if (caught instanceof ApiError) {
          setError(caught.message);
          setCorrelationId(caught.correlationId ?? "");
        } else {
          setError(
            caught instanceof Error
              ? caught.message
              : "Unable to load the banking dashboard",
          );
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void loadDashboard();

    return () => {
      cancelled = true;
    };
  }, [session, refreshVersion]);

  const activeAccounts = useMemo(
    () => accounts.filter((account) => account.status === "ACTIVE").length,
    [accounts],
  );

  const currencies = useMemo(
    () => new Set(accounts.map((account) => account.currency)).size,
    [accounts],
  );

  function refreshDashboard() {
    setRefreshVersion((current) => current + 1);
  }

  if (loading) {
    return (
      <div className="grid gap-6">
        <div className="h-40 animate-pulse rounded-xl bg-slate-200" />

        <div className="grid gap-4 md:grid-cols-2">
          <div className="h-44 animate-pulse rounded-xl bg-slate-200" />
          <div className="h-44 animate-pulse rounded-xl bg-slate-200" />
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <Card className="border-red-200 bg-red-50">
        <CardHeader>
          <CardTitle className="text-red-900">
            Unable to load your accounts
          </CardTitle>
        </CardHeader>

        <CardContent className="space-y-2">
          <p className="text-sm text-red-700">{error}</p>

          {correlationId && (
            <p className="font-mono text-xs text-red-600">
              Correlation ID: {correlationId}
            </p>
          )}
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-8">
      <section>
        <p className="text-sm font-medium text-blue-600">Personal banking</p>

        <h1 className="mt-1 text-3xl font-semibold tracking-tight">
          Welcome back{customer ? `, ${customer.firstName}` : ""}
        </h1>

        <p className="mt-2 text-sm text-slate-500">
          View your accounts, move money and review recent activity.
        </p>
      </section>

      <section className="grid gap-4 sm:grid-cols-3">
        <SummaryCard label="Accounts" value={String(accounts.length)} />

        <SummaryCard label="Active accounts" value={String(activeAccounts)} />

        <SummaryCard label="Currencies" value={String(currencies)} />
      </section>

      {customer && (
        <Card>
          <CardHeader>
            <CardTitle>Customer profile</CardTitle>
          </CardHeader>

          <CardContent>
            <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
              <Detail label="First name" value={customer.firstName} />

              <Detail label="Last name" value={customer.lastName} />

              <Detail label="Status" value={customer.status} />

              <Detail label="Customer ID" value={customer.id} mono />
            </div>
          </CardContent>
        </Card>
      )}

      <TransferPanel accounts={accounts} onCompleted={refreshDashboard} />

      <CashActionsPanel accounts={accounts} onCompleted={refreshDashboard} />

      <section>
        <div className="mb-4">
          <h2 className="text-xl font-semibold">Your accounts</h2>

          <p className="mt-1 text-sm text-slate-500">
            Current balances and recent ledger activity.
          </p>
        </div>

        {accounts.length === 0 ? (
          <Card>
            <CardContent className="py-10 text-center text-sm text-slate-500">
              No accounts are available for this customer.
            </CardContent>
          </Card>
        ) : (
          <div className="grid gap-4 lg:grid-cols-2">
            {accounts.map((account) => (
              <AccountCard
                key={`${account.id}-${refreshVersion}`}
                account={account}
              />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

function SummaryCard({ label, value }: { label: string; value: string }) {
  return (
    <Card>
      <CardContent className="p-5">
        <p className="text-sm text-slate-500">{label}</p>

        <p className="mt-2 text-3xl font-semibold tracking-tight">{value}</p>
      </CardContent>
    </Card>
  );
}

function AccountCard({ account }: { account: Account }) {
  const formattedBalance = new Intl.NumberFormat("en-GB", {
    style: "currency",
    currency: account.currency,
  }).format(account.balance);

  return (
    <Card className="overflow-hidden">
      <CardContent className="p-0">
        <div className="bg-slate-950 px-6 py-5 text-white">
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-sm text-slate-400">
                {account.type === "CURRENT"
                  ? "Current account"
                  : "Savings account"}
              </p>

              <p className="mt-1 font-mono text-sm">{account.accountNumber}</p>
            </div>

            <AccountStatus status={account.status} />
          </div>

          <p className="mt-8 text-sm text-slate-400">Available balance</p>

          <p className="mt-1 text-3xl font-semibold tracking-tight">
            {formattedBalance}
          </p>
        </div>

        <div className="grid gap-4 px-6 py-5 sm:grid-cols-2">
          <Detail label="Currency" value={account.currency} />

          <Detail label="Account ID" value={account.id} mono />
        </div>

        <AccountActivity accountId={account.id} />
      </CardContent>
    </Card>
  );
}

function AccountStatus({ status }: { status: Account["status"] }) {
  const styles = {
    ACTIVE: "border-emerald-400/20 bg-emerald-400/10 text-emerald-300",
    FROZEN: "border-amber-400/20 bg-amber-400/10 text-amber-300",
    CLOSED: "border-slate-400/20 bg-slate-400/10 text-slate-300",
  };

  return (
    <span
      className={`rounded-full border px-2.5 py-1 text-xs font-medium ${styles[status]}`}
    >
      {status}
    </span>
  );
}

function Detail({
  label,
  value,
  mono = false,
}: {
  label: string;
  value: string;
  mono?: boolean;
}) {
  return (
    <div className="min-w-0">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-400">
        {label}
      </p>

      <p
        className={`mt-1 break-all text-sm text-slate-800 ${
          mono ? "font-mono text-xs" : ""
        }`}
      >
        {value}
      </p>
    </div>
  );
}
