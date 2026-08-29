import { useMemo, useState, type FormEvent } from "react";

import { ApiError } from "@/api/api-client";
import type { Account } from "@/api/account-api";
import {
  createIdempotencyKey,
  transfer,
  type Transaction,
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

interface TransferPanelProps {
  accounts: Account[];
  onCompleted: () => void;
}

interface RetryState {
  fingerprint: string;
  idempotencyKey: string;
}

export function TransferPanel({ accounts, onCompleted }: TransferPanelProps) {
  const { session } = useSession();

  const activeAccounts = useMemo(
    () => accounts.filter((account) => account.status === "ACTIVE"),
    [accounts],
  );

  const [sourceAccountId, setSourceAccountId] = useState("");
  const [destinationAccountId, setDestinationAccountId] = useState("");
  const [amount, setAmount] = useState("");

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [correlationId, setCorrelationId] = useState("");
  const [result, setResult] = useState<Transaction | null>(null);
  const [retryState, setRetryState] = useState<RetryState | null>(null);

  const sourceAccount =
    activeAccounts.find((account) => account.id === sourceAccountId) ??
    activeAccounts[0];

  const destinationAccount =
    activeAccounts.find((account) => account.id === destinationAccountId) ??
    activeAccounts.find((account) => account.id !== sourceAccount?.id);

  const parsedAmount = Number(amount);

  const currentFingerprint =
    sourceAccount && destinationAccount && Number.isFinite(parsedAmount)
      ? JSON.stringify({
          sourceAccountId: sourceAccount.id,
          destinationAccountId: destinationAccount.id,
          amount: parsedAmount,
          currency: sourceAccount.currency,
        })
      : "";

  const isRetry =
    Boolean(retryState) && retryState?.fingerprint === currentFingerprint;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!session || !sourceAccount || !destinationAccount) {
      return;
    }

    if (!Number.isFinite(parsedAmount) || parsedAmount <= 0) {
      setError("Enter an amount greater than zero.");
      return;
    }

    if (sourceAccount.id === destinationAccount.id) {
      setError("Choose a different destination account.");
      return;
    }

    if (sourceAccount.currency !== destinationAccount.currency) {
      setError("Source and destination currencies must match.");
      return;
    }

    const fingerprint = JSON.stringify({
      sourceAccountId: sourceAccount.id,
      destinationAccountId: destinationAccount.id,
      amount: parsedAmount,
      currency: sourceAccount.currency,
    });

    const idempotencyKey =
      retryState?.fingerprint === fingerprint
        ? retryState.idempotencyKey
        : createIdempotencyKey("TRANSFER");

    setRetryState({
      fingerprint,
      idempotencyKey,
    });

    setLoading(true);
    setError("");
    setCorrelationId("");
    setResult(null);

    try {
      const response = await transfer(
        {
          sourceAccountId: sourceAccount.id,
          destinationAccountId: destinationAccount.id,
          amount: parsedAmount,
          currency: sourceAccount.currency,
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
            : "The transfer result is unknown",
        );
      }
    } finally {
      setLoading(false);
    }
  }

  if (activeAccounts.length < 2) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Transfer money</CardTitle>

          <CardDescription>
            At least two active accounts are required for an internal transfer.
          </CardDescription>
        </CardHeader>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Transfer money</CardTitle>

        <CardDescription>
          Move money between your active AS Bank accounts.
        </CardDescription>
      </CardHeader>

      <CardContent>
        <form
          onSubmit={submit}
          className="grid gap-5 lg:grid-cols-[1fr_1fr_0.7fr_auto]"
        >
          <div className="space-y-2">
            <label
              htmlFor="sourceAccount"
              className="text-sm font-medium text-slate-700"
            >
              From
            </label>

            <select
              id="sourceAccount"
              value={sourceAccount?.id ?? ""}
              onChange={(event) => {
                setSourceAccountId(event.target.value);
                setError("");
                setResult(null);
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
              htmlFor="destinationAccount"
              className="text-sm font-medium text-slate-700"
            >
              To
            </label>

            <select
              id="destinationAccount"
              value={destinationAccount?.id ?? ""}
              onChange={(event) => {
                setDestinationAccountId(event.target.value);
                setError("");
                setResult(null);
              }}
              className="h-11 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm outline-none focus:border-blue-500 focus:ring-4 focus:ring-blue-500/10"
            >
              {activeAccounts
                .filter((account) => account.id !== sourceAccount?.id)
                .map((account) => (
                  <option key={account.id} value={account.id}>
                    {account.type} · {account.accountNumber}
                  </option>
                ))}
            </select>
          </div>

          <div className="space-y-2">
            <label
              htmlFor="transferAmount"
              className="text-sm font-medium text-slate-700"
            >
              Amount
            </label>

            <div className="relative">
              <input
                id="transferAmount"
                type="number"
                min="0.01"
                step="0.01"
                value={amount}
                onChange={(event) => {
                  setAmount(event.target.value);
                  setError("");
                  setResult(null);
                }}
                placeholder="100.00"
                className="h-11 w-full rounded-lg border border-slate-200 bg-white px-3 pr-14 text-sm outline-none focus:border-blue-500 focus:ring-4 focus:ring-blue-500/10"
              />

              <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-medium text-slate-400">
                {sourceAccount?.currency}
              </span>
            </div>
          </div>

          <div className="flex items-end">
            <Button
              type="submit"
              disabled={loading || !amount}
              className="h-11 w-full bg-blue-600 px-6 text-white hover:bg-blue-700 lg:w-auto"
            >
              {loading
                ? "Processing..."
                : isRetry
                  ? "Retry transfer"
                  : "Transfer"}
            </Button>
          </div>
        </form>

        {error && (
          <div className="mt-5 rounded-lg border border-red-200 bg-red-50 px-4 py-3">
            <p className="text-sm font-medium text-red-900">
              Transfer not completed
            </p>

            <p className="mt-1 text-sm text-red-700">{error}</p>

            {retryState && (
              <p className="mt-2 text-xs text-red-600">
                Retrying the same transfer will reuse its idempotency key.
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
                ? "Transfer completed"
                : `Transfer ${result.status.toLowerCase()}`}
            </p>

            <p className="mt-1 text-sm text-slate-600">Transaction ID</p>

            <p className="mt-1 break-all font-mono text-xs text-slate-700">
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
