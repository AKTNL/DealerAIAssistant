const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

export class ApiError extends Error {
  constructor(message, { status = 0, body = "" } = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.body = body;
  }
}

export function buildUrl(path) {
  return `${API_BASE_URL}${path}`;
}

export async function requestJson(path, options = {}) {
  const { headers, ...fetchOptions } = options;
  const response = await fetch(buildUrl(path), {
    ...fetchOptions,
    headers: {
      "Content-Type": "application/json",
      ...(headers ?? {})
    }
  });

  if (!response.ok) {
    const body = await response.text();
    throw new ApiError(extractErrorMessage(body) || `Request failed with status ${response.status}`, {
      status: response.status,
      body
    });
  }

  return response.json();
}

export function extractErrorMessage(body) {
  if (!body) {
    return "";
  }

  try {
    const parsed = JSON.parse(body);
    return parsed?.message ?? parsed?.error ?? body;
  } catch {
    return body;
  }
}
