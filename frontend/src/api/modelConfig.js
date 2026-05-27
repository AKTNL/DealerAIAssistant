import { requestJson } from "./client";
import { getAuthToken } from "./sessionToken";

export function testModelConnection(modelConfig) {
  const token = getAuthToken();

  return requestJson("/api/model-config/test", {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: JSON.stringify(modelConfig)
  });
}
