import { describe, expect, it } from "vitest";
import { getModelErrorMessage } from "../modelErrors";

describe("getModelErrorMessage", () => {
  it("maps backend config failure text to the invalid-config message", () => {
    expect(
      getModelErrorMessage("Chat request failed with the provided model configuration.")
    ).toBe("The model settings are incomplete or invalid. Check them and try again.");
  });

  it("maps empty or malformed model replies to a response-specific message", () => {
    expect(getModelErrorMessage("Reply is blank after model generation.")).toBe(
      "The model returned an empty or invalid response. Please try again."
    );
  });

  it("maps oversized streamed replies to a size-specific message", () => {
    expect(getModelErrorMessage("The streamed reply exceeded the allowed output limit.")).toBe(
      "The model response was too large to process. Try a shorter or narrower request."
    );
  });

  it("maps expired app login sessions separately from upstream model auth", () => {
    expect(getModelErrorMessage("Login session expired.", { authExpired: "Please sign in again." }, "en")).toBe(
      "Please sign in again."
    );
  });
});
