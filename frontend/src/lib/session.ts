let accessToken: string | undefined;
let customerId: string | undefined;

export function setSession(token: string, currentCustomerId: string): void {
  accessToken = token;
  customerId = currentCustomerId;
}

export function clearSession(): void {
  accessToken = undefined;
  customerId = undefined;
}

export function getAccessToken(): string {
  if (!accessToken) {
    throw new Error("No access token is available");
  }

  return accessToken;
}

export function getCustomerId(): string {
  if (!customerId) {
    throw new Error("No customer is selected");
  }

  return customerId;
}

export function hasSession(): boolean {
  return Boolean(accessToken && customerId);
}
