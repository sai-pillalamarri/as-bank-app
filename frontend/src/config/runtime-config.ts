export interface RuntimeConfig {
  environment: string;
  apiBaseUrl: string;
}

let runtimeConfig: RuntimeConfig | undefined;

export async function loadRuntimeConfig(): Promise<void> {
  const response = await fetch("/config.json", {
    cache: "no-store",
  });

  if (!response.ok) {
    throw new Error(`Unable to load configuration: HTTP ${response.status}`);
  }

  runtimeConfig = (await response.json()) as RuntimeConfig;
}

export function getRuntimeConfig(): RuntimeConfig {
  if (!runtimeConfig) {
    throw new Error("Runtime configuration has not been loaded");
  }

  return runtimeConfig;
}
