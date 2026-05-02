import { requestJson } from "./client";

export function verifyAccessKey(key) {
  return requestJson("/api/auth/verify", {
    method: "POST",
    body: JSON.stringify({ key })
  });
}
