import { requestJson } from "./client";
import { getAuthToken } from "./sessionToken";

export async function getDataStatus() {
  const token = getAuthToken();
  const response = await requestJson("/api/data-status", {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  });
  const data = response?.data;

  return {
    fallbackActive: data?.fallbackActive === true,
    source: typeof data?.source === "string" ? data.source : "pending"
  };
}
