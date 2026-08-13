import { requestJson } from "./client";

export async function getModelConfig() {
  return requestJson("/api/model-config");
}

export function saveModelConfig(modelConfig) {
  return requestJson("/api/model-config", {
    method: "PUT",
    body: JSON.stringify(modelConfig)
  });
}

export function deleteModelConfig() {
  return requestJson("/api/model-config", { method: "DELETE" });
}

export function testModelConnection(modelConfig) {
  return requestJson("/api/model-config/test", {
    method: "POST",
    body: JSON.stringify(modelConfig)
  });
}
