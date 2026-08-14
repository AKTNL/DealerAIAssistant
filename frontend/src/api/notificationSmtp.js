import { requestJson } from "./client";

export async function getSmtpConfig() {
  return readData(await requestJson("/api/notification/smtp"), null);
}

export async function saveSmtpConfig(input) {
  return readData(await requestJson("/api/notification/smtp", {
    method: "PUT",
    body: JSON.stringify(input)
  }), null);
}

export async function deleteSmtpConfig(version) {
  await requestJson("/api/notification/smtp", {
    method: "DELETE",
    body: JSON.stringify({ version })
  });
}

export async function testSmtpConfig() {
  return readData(await requestJson("/api/notification/smtp/test", {
    method: "POST"
  }), null);
}

function readData(response, fallback) {
  return response?.data ?? fallback;
}
