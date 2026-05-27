const DEFAULT_MESSAGES = {
  en: {
    modelErrorAuth:
      "The API key is invalid or does not have permission. Check the model settings and try again.",
    authExpired: "Your login session has expired. Please sign in again.",
    modelErrorBusy: "The model is busy right now. Please try again shortly.",
    modelErrorGeneric: "The model request failed. Check the settings and try again.",
    modelErrorInvalidConfig: "The model settings are incomplete or invalid. Check them and try again.",
    modelErrorInvalidResponse: "The model returned an empty or invalid response. Please try again.",
    modelErrorOutputLimit:
      "The model response was too large to process. Try a shorter or narrower request.",
    modelErrorTimeout: "The model response timed out. Please try again shortly.",
    modelErrorUnavailable:
      "The model service is temporarily unavailable. Check the base URL or network connection."
  },
  zh: {
    authExpired: "登录状态已过期，请重新登录。",
    modelErrorAuth: "API Key 无效或当前账号没有该模型权限，请检查模型配置。",
    modelErrorBusy: "当前模型繁忙，请稍后重试。",
    modelErrorGeneric: "模型调用失败，请检查配置后重试。",
    modelErrorInvalidConfig: "模型配置不完整或格式不正确，请检查后重试。",
    modelErrorInvalidResponse: "模型返回了空内容或无效内容，请重试。",
    modelErrorOutputLimit: "模型返回内容过长，无法处理。请缩短问题或缩小范围后重试。",
    modelErrorTimeout: "模型响应超时，请稍后重试。",
    modelErrorUnavailable: "模型服务暂时不可用，请检查 Base URL 或网络连接。"
  }
};

export function getModelErrorMessage(error, dictionary = {}, locale = "en") {
  const rawMessage = normalizeErrorMessage(error);
  const normalized = rawMessage.toLowerCase();

  if (looksLikeSessionExpiredError(normalized)) {
    return readMessage(dictionary, locale, "authExpired");
  }

  if (looksLikeBusyError(rawMessage, normalized)) {
    return readMessage(dictionary, locale, "modelErrorBusy");
  }

  if (looksLikeAuthError(rawMessage, normalized)) {
    return readMessage(dictionary, locale, "modelErrorAuth");
  }

  if (looksLikeTimeoutError(normalized)) {
    return readMessage(dictionary, locale, "modelErrorTimeout");
  }

  if (looksLikeUnavailableError(normalized)) {
    return readMessage(dictionary, locale, "modelErrorUnavailable");
  }

  if (looksLikeInvalidConfigError(normalized)) {
    return readMessage(dictionary, locale, "modelErrorInvalidConfig");
  }

  if (looksLikeOutputLimitError(normalized)) {
    return readMessage(dictionary, locale, "modelErrorOutputLimit");
  }

  if (looksLikeInvalidResponseError(normalized)) {
    return readMessage(dictionary, locale, "modelErrorInvalidResponse");
  }

  return readMessage(dictionary, locale, "modelErrorGeneric");
}

function normalizeErrorMessage(error) {
  if (typeof error === "string") {
    return error;
  }

  if (error instanceof Error) {
    return error.message ?? "";
  }

  if (error && typeof error === "object") {
    return error.message ?? error.error?.message ?? JSON.stringify(error);
  }

  return "";
}

function looksLikeAuthError(rawMessage, normalized) {
  return normalized.includes("401")
    || normalized.includes("403")
    || normalized.includes("unauthorized")
    || normalized.includes("authentication")
    || normalized.includes("invalid api key")
    || normalized.includes("auth failed")
    || normalized.includes("forbidden")
    || rawMessage.includes("没有权限");
}

function looksLikeSessionExpiredError(normalized) {
  return normalized.includes("login session expired")
    || normalized.includes("session expired");
}

function looksLikeBusyError(rawMessage, normalized) {
  return normalized.includes("429")
    || normalized.includes("1305")
    || normalized.includes("too many requests")
    || normalized.includes("rate limit")
    || normalized.includes("busy")
    || rawMessage.includes("访问量过大")
    || rawMessage.includes("稍后再试")
    || rawMessage.includes("模型繁忙")
    || rawMessage.includes("限流");
}

function looksLikeTimeoutError(normalized) {
  return normalized.includes("timeout") || normalized.includes("timed out");
}

function looksLikeUnavailableError(normalized) {
  return normalized.includes("unknownhost")
    || normalized.includes("connection refused")
    || normalized.includes("unreachable")
    || normalized.includes("i/o error")
    || normalized.includes("failed to fetch")
    || normalized.includes("networkerror")
    || normalized.includes("load failed");
}

function looksLikeInvalidConfigError(normalized) {
  return normalized.includes("invalid base url")
    || normalized.includes("model settings are required")
    || normalized.includes("base url, api key, and model are required")
    || normalized.includes("must not be blank")
    || normalized.includes("invalid model settings")
    || normalized.includes("provided model configuration");
}

function looksLikeOutputLimitError(normalized) {
  return normalized.includes("exceeded the allowed output limit");
}

function looksLikeInvalidResponseError(normalized) {
  return normalized.includes("configured model returned an empty response")
    || normalized.includes("reply is blank after model generation");
}

function readMessage(dictionary, locale, key) {
  const language = locale === "zh" ? "zh" : "en";
  return dictionary?.[key] ?? DEFAULT_MESSAGES[language][key];
}
