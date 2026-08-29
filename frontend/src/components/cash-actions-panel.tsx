import { useMemo, useState, type FormEvent } from "react";

import { ApiError } from "@/api/api-client";
import type { Account } from "@/api/account-api";
import {
  createIdempotencyKey,
  deposit,
  withdraw,
  type Transaction,
  type TransactionType,
} from "@/api/transaction-api";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { useSession } from "@/lib/session-context";

type ActionMode = "DEPOSIT" | "WITHDRAWAL";

interface CashActionsPanelProps {
  accounts: Account[];
  onCompleted: () => void;
}

interface RetryState {
  fingerprint: string;
  idempotencyKey: string;
}

export function CashActionsPanel({
  accounts,
  onCompleted,
}: CashActionsPanelProps) {
  const { session } = useSession();

  const activeAccounts = useMemo(
    () => accounts.filter((account) => account.status === "ACTIVE"),
    [accounts],
  );

  const [mode, setMode] = useState<ActionMode>("DEPOSIT");
  const [accountId, setAccountId] = useState("");
  const [amount, setAmount] = useState("");

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [correlationId, setCorrelationId] = useState("");
  const [result, setResult] = useState<Transaction | null>(null);
  const [retryState, setRetryState] = useState<RetryState | null>(null);

  const selectedAccount =
    activeAccounts.find((account) => account.id === accountId) ??
    activeAccounts[0];

  const parsedAmount = Number(amount);

  const currentFingerprint =
    selectedAccount && Number.isFinite(parsedAmount)
      ? JSON.stringify({
          mode,
          accountId: selectedAccount.id,
          amount: parsedAmount,
          currency: selectedAccount.currency,
        })
      : "";

  const isRetry = retryState?.fingerprint === currentFingerprint;

  function resetFeedback() {
    setError("");
    setCorrelationId("");
    setResult(null);
  }

  function changeMode(nextMode: ActionMode) {
    setMode(nextMode);
    setRetryState(null);
    resetFeedback();
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!session || !selectedAccount) {
      return;
    }

    if (!Number.isFinite(parsedAmount) || parsedAmount <= 0) {
      setError("Enter an amount greater than zero.");
      return;
    }

    const fingerprint = JSON.stringify({
      mode,
      accountId: selectedAccount.id,
      amount: parsedAmount,
      currency: selectedAccount.currency,
    });

    const idempotencyKey =
      retryState?.fingerprint === fingerprint
        ? retryState.idempotencyKey
        : createIdempotencyKey(mode as TransactionType);

    setRetryState({
      fingerprint,
      idempotencyKey,
    });

    setLoading(true);
    resetFeedback();

    try {
      const response =
        mode === "DEPOSIT"
          ? await deposit(
              {
                destinationAccountId: selectedAccount.id,
                amount: parsedAmount,
                currency: selectedAccount.currency,
              },
              session.accessToken,
              idempotencyKey,
            )
          : await withdraw(
              {
                sourceAccountId: selectedAccount.id,
                amount: parsedAmount,
                currency: selectedAccount.currency,
              },
              session.accessToken,
              idempotencyKey,
            );

      setResult(response);
      setRetryState(null);

      if (response.status === "APPLIED") {
        setAmount("");
        onCompleted();
      }
    } catch (caught) {
      if (caught instanceof ApiError) {
        setError(caught.message);
        setCorrelationId(caught.correlationId ?? "");
      } else {
        setError(
          caught instanceof Error
            ? caught.message
            : "The transaction result is unknown",
        );
      }
    } finally {
      setLoading(false);
    }
  }

  if (activeAccounts.length === 0) {
    return null;
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Money in and out</CardTitle>

        <CardDescription>
          Deposit into an account or withdraw from an available balance.
        </CardDescription>
      </CardHeader>

      <CardContent>
        <div className="mb-6 inline-flex rounded-lg bg-slate-100 p-1">
          <button
            type="button"
            onClick={() => changeMode("DEPOSIT")}
            className={`rounded-md px-4 py-2 text-sm font-medium transition ${
              mode === "DEPOSIT"
                ? "bg-white text-slate-950 shadow-sm"
                : "text-slate-500 hover:text-slate-900"
            }`}
          >
            Deposit
          </button>

          <button
            type="button"
            onClick={() => changeMode("WITHDRAWAL")}
            className={`rounded-md px-4 py-2 text-sm font-medium transition ${
              mode === "WITHDRAWAL"
                ? "bg-white text-slate-950 shadow-sm"
                : "text-slate-500 hover:text-slate-900"
            }`}
          >
            Withdraw
          </button>
        </div>

        <form
          onSubmit={submit}
          className="grid gap-5 md:grid-cols-[1fr_0.7fr_auto]"
        >
          <div className="space-y-2">
            <label
              htmlFor="cashAccount"
              className="text-sm font-medium text-slate-700"
            >
              Account
            </label>

            <select
              id="cashAccount"
              value={selectedAccount?.id ?? ""}
              onChange={(event) => {
                setAccountId(event.target.value);
                setRetryState(null);
                resetFeedback();
              }}
              className="h-11 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm outline-none focus:border-blue-500 focus:ring-4 focus:ring-blue-500/10"
            >
              {activeAccounts.map((account) => (
                <option key={account.id} value={account.id}>
                  {account.type} · {account.accountNumber}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-2">
            <label
              htmlFor="cashAmount"
              className="text-sm font-medium text-slate-700"
            >
              Amount
            </label>

            <div className="relative">
              <input
                id="cashAmount"
                type="number"
                min="0.01"
                step="0.01"
                value={amount}
                onChange={(event) => {
                  setAmount(event.target.value);
                  resetFeedback();
                }}
                placeholder="100.00"
                className="h-11 w-full rounded-lg border border-slate-200 bg-white px-3 pr-14 text-sm outline-none focus:border-blue-500 focus:ring-4 focus:ring-blue-500/10"
              />

              <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-medium text-slate-400">
                {selectedAccount?.currency}
              </span>
            </div>
          </div>

          <div className="flex items-end">
            <Button
              type="submit"
              disabled={loading || !amount}
              className="h-11 w-full bg-blue-600 px-6 text-white hover:bg-blue-700 md:w-auto"
            >
              {loading
                ? "Processing..."
                : isRetry
                  ? `Retry ${mode.toLowerCase()}`
                  : mode === "DEPOSIT"
                    ? "Deposit"
                    : "Withdraw"}
            </Button>
          </div>
        </form>

        {mode === "WITHDRAWAL" && selectedAccount && (
          <p className="mt-3 text-xs text-slate-500">
            Available balance:{" "}
            {new Intl.NumberFormat("en-GB", {
              style: "currency",
              currency: selectedAccount.currency,
            }).format(selectedAccount.balance)}
          </p>
        )}

        {error && (
          <div className="mt-5 rounded-lg border border-red-200 bg-red-50 px-4 py-3">
            <p className="text-sm font-medium text-red-900">
              Transaction not completed
            </p>

            <p className="mt-1 text-sm text-red-700">{error}</p>

            {retryState && (
              <p className="mt-2 text-xs text-red-600">
                Retrying without changing the request will reuse its idempotency
                key.
              </p>
            )}

            {correlationId && (
              <p className="mt-2 break-all font-mono text-xs text-red-500">
                Correlation ID: {correlationId}
              </p>
            )}
          </div>
        )}

        {result && (
          <div
            className={`mt-5 rounded-lg border px-4 py-3 ${
              result.status === "APPLIED"
                ? "border-emerald-200 bg-emerald-50"
                : "border-amber-200 bg-amber-50"
            }`}
          >
            <p
              className={`text-sm font-medium ${
                result.status === "APPLIED"
                  ? "text-emerald-900"
                  : "text-amber-900"
              }`}
            >
              {result.status === "APPLIED"
                ? mode === "DEPOSIT"
                  ? "Deposit completed"
                  : "Withdrawal completed"
                : `Transaction ${result.status.toLowerCase()}`}
            </p>

            <p className="mt-2 break-all font-mono text-xs text-slate-600">
              {result.id}
            </p>

            {result.failureReason && (
              <p className="mt-2 text-sm text-amber-800">
                {result.failureReason.replaceAll("_", " ")}
              </p>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
