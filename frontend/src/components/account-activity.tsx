import { useEffect, useState } from "react";

import { ApiError } from "@/api/api-client";
import { getTransactionHistory, type LedgerEntry } from "@/api/transaction-api";
import { useSession } from "@/lib/session-context";

interface AccountActivityProps {
  accountId: string;
}

export function AccountActivity({ accountId }: AccountActivityProps) {
  const { session } = useSession();

  const [entries, setEntries] = useState<LedgerEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [correlationId, setCorrelationId] = useState("");

  useEffect(() => {
    if (!session) {
      return;
    }

    const accessToken = session.accessToken;

    let cancelled = false;

    async function loadHistory() {
      setLoading(true);
      setError("");
      setCorrelationId("");

      try {
        const response = await getTransactionHistory(accountId, accessToken);

        if (!cancelled) {
          setEntries(response.items);
        }
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
              : "Unable to load transaction history",
          );
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void loadHistory();

    return () => {
      cancelled = true;
    };
  }, [accountId, session]);

  if (loading) {
    return (
      <div className="border-t border-slate-100 px-6 py-5">
        <div className="h-16 animate-pulse rounded-lg bg-slate-100" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="border-t border-slate-100 px-6 py-5">
        <p className="text-sm font-medium text-red-700">
          Unable to load activity
        </p>

        <p className="mt-1 text-xs text-red-600">{error}</p>

        {correlationId && (
          <p className="mt-2 break-all font-mono text-xs text-red-500">
            Correlation ID: {correlationId}
          </p>
        )}
      </div>
    );
  }

  return (
    <div className="border-t border-slate-100 px-6 py-5">
      <div className="mb-4 flex items-center justify-between">
        <div>
          <p className="font-medium text-slate-900">Recent activity</p>

          <p className="text-xs text-slate-500">Latest ledger entries</p>
        </div>

        <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-600">
          {entries.length}
        </span>
      </div>

      {entries.length === 0 ? (
        <p className="rounded-lg bg-slate-50 px-4 py-4 text-sm text-slate-500">
          No transactions yet.
        </p>
      ) : (
        <div className="divide-y divide-slate-100">
          {entries.map((entry) => (
            <ActivityRow key={entry.id} entry={entry} />
          ))}
        </div>
      )}
    </div>
  );
}

function ActivityRow({ entry }: { entry: LedgerEntry }) {
  const isDebit = entry.direction === "DEBIT";

  const formattedAmount = new Intl.NumberFormat("en-GB", {
    style: "currency",
    currency: entry.currency,
  }).format(entry.amount);

  const formattedDate = new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(entry.createdAt));

  return (
    <div className="flex items-center justify-between gap-4 py-4">
      <div className="min-w-0">
        <div className="flex items-center gap-2">
          <span
            className={`flex size-8 shrink-0 items-center justify-center rounded-full text-sm font-semibold ${
              isDebit
                ? "bg-red-50 text-red-600"
                : "bg-emerald-50 text-emerald-600"
            }`}
          >
            {isDebit ? "−" : "+"}
          </span>

          <div className="min-w-0">
            <p className="text-sm font-medium text-slate-900">
              {isDebit ? "Money out" : "Money in"}
            </p>

            <p className="truncate text-xs text-slate-500">{formattedDate}</p>
          </div>
        </div>
      </div>

      <div className="text-right">
        <p
          className={`text-sm font-semibold ${
            isDebit ? "text-slate-900" : "text-emerald-700"
          }`}
        >
          {isDebit ? "−" : "+"}
          {formattedAmount}
        </p>

        <p className="mt-1 font-mono text-[10px] text-slate-400">
          {entry.transactionId.slice(0, 8)}
        </p>
      </div>
    </div>
  );
}
