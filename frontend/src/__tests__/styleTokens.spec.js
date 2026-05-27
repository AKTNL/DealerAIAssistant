import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const stylePath = resolve(process.cwd(), "src/style.css");
const stylesheet = readFileSync(stylePath, "utf8");

function expectCustomProperty(name, value) {
  const pattern = new RegExp(`${name}\\s*:\\s*${value}\\s*;`);
  expect(stylesheet).toMatch(pattern);
}

function expectSelectorInRule(selector, expectedDeclarations) {
  const pattern = /([^{}]+)\{([\s\S]*?)\}/g;
  let match;

  while ((match = pattern.exec(stylesheet)) !== null) {
    const selectors = match[1]
      .replace(/\/\*[\s\S]*?\*\//g, "")
      .split(",")
      .map((candidate) => candidate.trim());

    if (
      selectors.includes(selector) &&
      expectedDeclarations.every((declaration) => match[2].includes(declaration))
    ) {
      return;
    }
  }

  throw new Error(
    `Did not find selector ${selector} with declarations: ${expectedDeclarations.join(", ")}`
  );
}

function expectSelectorsShareRule(selectors, expectedDeclarations) {
  const selectorPattern = selectors
    .map((selector) => selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))
    .join("\\s*,\\s*");
  const pattern = new RegExp(`${selectorPattern}\\s*\\{([\\s\\S]*?)\\}`, "g");
  const matches = [...stylesheet.matchAll(pattern)];

  expect(matches.length, `Missing shared rule for ${selectors.join(", ")}`).toBeGreaterThan(0);
  const matchingRule = matches.find((match) =>
    expectedDeclarations.every((declaration) => match[1].includes(declaration))
  );

  expect(
    matchingRule,
    `Missing declarations ${expectedDeclarations.join(", ")} for shared rule ${selectors.join(", ")}`
  ).toBeDefined();
}

function expectMediaBlockContains(condition, snippets) {
  const pattern = new RegExp(`@media \\(${condition}\\)\\s*\\{([\\s\\S]*?)\\n\\}`, "m");
  const match = stylesheet.match(pattern);

  expect(match, `Missing @media (${condition}) block`).not.toBeNull();
  for (const snippet of snippets) {
    expect(match[1]).toContain(snippet);
  }
}

function expectSupportsBlockContains(condition, snippets) {
  const pattern = new RegExp(`@supports \\(${condition}\\)\\s*\\{([\\s\\S]*?)\\n\\}`, "m");
  const match = stylesheet.match(pattern);

  expect(match, `Missing @supports (${condition}) block`).not.toBeNull();
  for (const snippet of snippets) {
    expect(match[1]).toContain(snippet);
  }
}

describe("blue gray enterprise style tokens", () => {
  test("defines the approved blue-gray enterprise tokens and keeps the neutral token API", () => {
    expectCustomProperty("--page-bg", "#f4f7fb");
    expectCustomProperty("--page-bg-deep", "#e8eef5");
    expectCustomProperty("--surface", "#ffffff");
    expectCustomProperty("--surface-strong", "#f8fbff");
    expectCustomProperty("--surface-soft", "#eef4fa");
    expectCustomProperty("--surface-muted", "#e2ebf3");
    expectCustomProperty("--surface-tinted", "#e9f2fb");
    expectCustomProperty("--sidebar-bg", "#eaf1f8");
    expectCustomProperty("--border-soft", "#c9d6e3");
    expectCustomProperty("--border-strong", "#aebfce");
    expectCustomProperty("--border-focus", "#3f6f9f");
    expectCustomProperty("--brand-primary", "#17324d");
    expectCustomProperty("--brand-primary-hover", "#22527a");
    expectCustomProperty("--brand-primary-soft", "#dceaf6");
    expectCustomProperty("--accent-info", "#2f7da8");
    expectCustomProperty("--text-main", "#102033");
    expectCustomProperty("--text-muted", "#516173");
    expectCustomProperty("--text-helper", "#748397");
    expectCustomProperty("--text-strong", "#0b1728");
    expectCustomProperty("--danger", "#a43f3f");
    expectCustomProperty("--focus-ring", "0 0 0 3px rgba\\(47, 125, 168, 0\\.18\\)");

    expect(stylesheet).not.toContain("linear-gradient(135deg, #76ad87, #558667)");
    expect(stylesheet).not.toMatch(/rgba\(110,\s*167,\s*129/i);
  });

  test("uses blue-gray theme tokens for primary, focus, hover, pending, and loading states", () => {
    expectSelectorsShareRule([".primary-sidebar-button", ".primary-button"], [
      "border: 1px solid var(--brand-primary);",
      "background: var(--brand-primary);",
      "color: #ffffff;"
    ]);

    expectSelectorsShareRule([".primary-sidebar-button:hover", ".primary-button:hover"], [
      "background: var(--brand-primary-hover);",
      "border-color: var(--brand-primary-hover);"
    ]);

    expectSelectorsShareRule([".composer-card:focus-within", ".composer-card-editorial:focus-within"], [
      "border-color: var(--border-focus);",
      "box-shadow: var(--shadow-md), var(--focus-ring);"
    ]);

    expectSelectorInRule(".text-input:focus", [
      "border-color: var(--border-focus);",
      "box-shadow: var(--focus-ring);"
    ]);

    expectSelectorInRule(".message-card-user", [
      "background: var(--brand-primary);",
      "color: #ffffff;"
    ]);

    expectSelectorInRule(".message-card-assistant", [
      "border-left: 3px solid var(--brand-primary);"
    ]);

    expectSelectorInRule(".message-bubble-user", [
      "background: var(--brand-primary);",
      "color: #ffffff;"
    ]);

    expectSelectorInRule(".message-bubble-assistant", [
      "color: var(--text-main);"
    ]);

    expectSelectorInRule(".message-bubble-assistant .markdown-body", [
      "color: var(--text-main);"
    ]);

    expectSelectorInRule(".message-user .markdown-body h1", [
      "color: #ffffff;"
    ]);

    expectSelectorInRule(".login-submit-button", [
      "border: 1px solid var(--brand-primary);",
      "background: linear-gradient(135deg, var(--brand-primary-hover), var(--brand-primary));"
    ]);

    expectSelectorInRule(".login-glass-shell", [
      "background: radial-gradient(circle at 50% -20%, rgba(47, 125, 168, 0.08) 0%, var(--page-bg) 60%);"
    ]);

    expectSelectorInRule(".login-bg-glow", [
      "background: radial-gradient(circle at 50% -20%, rgba(47, 125, 168, 0.08) 0%, var(--page-bg) 60%);"
    ]);

    expectSelectorInRule(".login-card-top-bar", [
      "background: linear-gradient(90deg, transparent, var(--accent-info), transparent);"
    ]);

    expectSelectorInRule(".login-logo-glow", [
      "background: var(--accent-info);"
    ]);

    expectSelectorInRule(".login-logo-img", [
      "filter: drop-shadow(0 1px 2px rgba(23, 50, 77, 0.14));"
    ]);

    expectSelectorInRule(".markdown-body tbody tr:hover", [
      "background-color: rgba(47, 125, 168, 0.06) !important;"
    ]);

    expectSelectorInRule(".mermaid-toggle-button[aria-pressed=\"true\"]", [
      "border-color: rgba(63, 111, 159, 0.24);"
    ]);

    expectSelectorInRule(".sidebar-module-question.is-pending", [
      "border-color: var(--border-focus);",
      "background: var(--brand-primary-soft);"
    ]);

    expectSelectorInRule(".sidebar-backdrop", [
      "background: rgba(23, 50, 77, 0.1);"
    ]);

    expectSelectorInRule(".topbar-icon-btn:disabled:hover", [
      "background: transparent;",
      "color: var(--text-muted);"
    ]);

    expectSelectorInRule(".timeline-step-spinner", [
      "border-top-color: var(--accent-info);"
    ]);

    expectSelectorInRule(".skeleton-spinner-icon", [
      "border-top-color: var(--accent-info);"
    ]);

    expect(stylesheet).not.toContain("background: linear-gradient(90deg, transparent, #111111, transparent);");
    expect(stylesheet).not.toContain("background: #111111;");
    expect(stylesheet).not.toContain("background: rgba(17, 17, 17, 0.08);");
    expect(stylesheet).not.toContain("rgba(17, 17, 17, 0.04)");
    expect(stylesheet).not.toContain("rgba(17, 17, 17, 0.12)");
  });

  test("keeps editorial alias classes in the same structural and responsive rule groups as the base shells", () => {
    expectSelectorsShareRule([".topbar", ".topbar-editorial"], [
      "position: sticky;",
      "padding: 0.75rem 1.4rem;",
      "border-bottom: 1px solid var(--border-soft);"
    ]);
    expectSelectorInRule(".topbar-editorial h2", [
      "font-size: 1rem;",
      "color: var(--text-strong);"
    ]);
    expectSelectorsShareRule([".composer-card", ".composer-card-editorial"], [
      "gap: 1rem;",
      "align-items: flex-end;",
      "background: var(--surface);"
    ]);
    expectSelectorsShareRule([".composer-card:focus-within", ".composer-card-editorial:focus-within"], [
      "border-color: var(--border-focus);",
      "box-shadow: var(--shadow-md), var(--focus-ring);"
    ]);
    expectMediaBlockContains("max-width: 1040px", [
      ".topbar,",
      ".topbar-editorial {",
      "padding-inline: 1rem;"
    ]);
    expectMediaBlockContains("max-width: 720px", [
      ".topbar,",
      ".topbar-editorial {",
      "padding: 0.9rem;"
    ]);
    expectMediaBlockContains("max-width: 720px", [
      ".chat-copy,",
      ".empty-state,",
      ".message-card,",
      ".composer-card,",
      ".composer-card-editorial {",
      "border-radius: 10px;"
    ]);
    expectMediaBlockContains("max-width: 720px", [
      ".composer-card,",
      ".composer-card-editorial {",
      "flex-direction: column;",
      "align-items: stretch;"
    ]);
  });

  test("keeps the global minimum width friendly to tablet-sized viewports", () => {
    expectSelectorInRule("body", [
      "min-width: 768px;"
    ]);

    expect(stylesheet).not.toContain("min-width: 1060px;");
  });
});

describe("chat analysis visual enhancements", () => {
  test("defines background, metric card, and chart hierarchy styles", () => {
    expectSelectorInRule(".chat-screen::before", [
      "background-image: linear-gradient(rgba(244, 247, 251, 0.84), rgba(244, 247, 251, 0.9)), url(\"/background.jpg\");",
      "background-size: cover;"
    ]);

    expectSelectorInRule(".analysis-metric-grid", [
      "display: grid;",
      "grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));"
    ]);

    expectSelectorInRule(".analysis-metric-value", [
      "font-size: 1.45rem;",
      "color: var(--brand-primary);"
    ]);

    expectSelectorInRule(".analysis-chart-panel", [
      "min-height: 380px;",
      "background: rgba(255, 255, 255, 0.94);"
    ]);

    expectSelectorInRule(".analysis-chart-canvas", [
      "height: 320px;"
    ]);
  });
});

describe("composer-input auto-resize", () => {
  it("declares all auto-resize CSS properties on .composer-input", () => {
    expectSelectorInRule(".composer-input", [
      "max-height: 200px;",
      "overflow-y: auto;",
    ]);

    expectSupportsBlockContains("field-sizing: content", [
      ".composer-input",
      "field-sizing: content;",
      "height: auto !important;",
    ]);
  });
});
