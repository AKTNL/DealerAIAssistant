import MarkdownIt from "markdown-it";
import hljs from "highlight.js/lib/core";
import bash from "highlight.js/lib/languages/bash";
import javascript from "highlight.js/lib/languages/javascript";
import java from "highlight.js/lib/languages/java";
import json from "highlight.js/lib/languages/json";
import markdownLanguage from "highlight.js/lib/languages/markdown";
import typescript from "highlight.js/lib/languages/typescript";
import xml from "highlight.js/lib/languages/xml";

hljs.registerLanguage("bash", bash);
hljs.registerLanguage("javascript", javascript);
hljs.registerLanguage("java", java);
hljs.registerLanguage("json", json);
hljs.registerLanguage("markdown", markdownLanguage);
hljs.registerLanguage("typescript", typescript);
hljs.registerLanguage("xml", xml);

const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  typographer: false,
  highlight(code, language) {
    if (language && hljs.getLanguage(language)) {
      return hljs.highlight(code, { language, ignoreIllegals: true }).value;
    }

    return hljs.highlightAuto(code, ["bash", "javascript", "java", "json", "markdown", "typescript", "xml"]).value;
  }
});

markdown.renderer.rules.link_open = (tokens, index, options, env, self) => {
  const token = tokens[index];
  token.attrSet("target", "_blank");
  token.attrSet("rel", "noopener noreferrer");
  return self.renderToken(tokens, index, options);
};

markdown.renderer.rules.fence = (tokens, index, options, env, self) => {
  const token = tokens[index];
  const info = token.info ? markdown.utils.unescapeAll(token.info).trim() : "";
  const langName = info ? escapeHtml(info.split(/\s+/g)[0]) : "";

  if (langName === "mermaid") {
    const source = normalizeMermaidSource(token.content);
    const escapedSource = escapeHtml(source);
    const blockId = createMermaidBlockId(source, env);

    return `<div class="mermaid-block" data-mermaid-block-id="${blockId}" data-view="chart"><pre class="mermaid-chart" data-mermaid-role="chart">${escapedSource}</pre><pre class="mermaid-source" data-mermaid-role="source"><code class="language-mermaid">${escapedSource}</code></pre></div>`;
  }

  if (langName === "chart-json") {
    const escapedJson = escapeHtml(String(token.content ?? "").trim());
    return `<div class="chart-json-block" data-chart-json="${escapedJson}"></div>`;
  }

  if (langName === "chart-empty") {
    const state = parseChartEmptyFence(token.content);
    const reason = escapeHtml(state.reason || "");
    const title = escapeHtml(state.title || "");
    const body = escapeHtml(state.body || "");

    return `<div class="analysis-empty-chart" role="status"${reason ? ` data-chart-empty-reason="${reason}"` : ""} data-chart-empty="true">${title ? `<div class="analysis-empty-chart-title">${title}</div>` : ""}${body ? `<div class="analysis-empty-chart-body">${body}</div>` : ""}</div>`;
  }

  const highlighted = options.highlight
    ? options.highlight(token.content, info, "")
    : escapeHtml(token.content);

  return `<pre class="hljs"><code class="hljs${langName ? ` language-${langName}` : ""}">${highlighted}</code></pre>`;
};

export function renderMarkdownLite(source) {
  const { markdownSource, htmlTables } = preserveHtmlTables(String(source ?? ""));
  const { source: chartReadySource, loadingPlaceholders } = replaceUnclosedChartJsonFence(markdownSource);
  const { source: wrappedSource, promotedBlocks } = wrapRawChartJson(chartReadySource);
  let rendered = markdown.render(wrappedSource);

  for (const { placeholder, html } of htmlTables) {
    rendered = replaceRenderedPlaceholder(rendered, placeholder, html);
  }

  for (const { placeholder, html } of loadingPlaceholders) {
    rendered = replaceRenderedPlaceholder(rendered, placeholder, html);
  }

  for (const { placeholder, html } of promotedBlocks) {
    rendered = replaceRenderedPlaceholder(rendered, placeholder, html);
  }

  return rendered;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function normalizeMermaidSource(value) {
  return String(value ?? "").replace(/\r\n?/g, "\n");
}

function parseChartEmptyFence(value) {
  const result = {};

  for (const line of String(value ?? "").replace(/\r\n?/g, "\n").split("\n")) {
    const match = line.match(/^([A-Za-z][A-Za-z0-9_-]*)\s*:\s*(.*)$/);

    if (!match) {
      continue;
    }

    const key = match[1];
    const field = key === "reason" || key === "title" || key === "body" ? key : null;

    if (field) {
      result[field] = match[2].trim();
    }
  }

  return result;
}

function replaceRenderedPlaceholder(rendered, placeholder, html) {
  return rendered
    .replaceAll(`<p>${placeholder}</p>\n`, `${html}\n`)
    .replaceAll(`<p>${placeholder}</p>`, html)
    .replaceAll(placeholder, html);
}

function replaceUnclosedChartJsonFence(source) {
  const match = findLastUnclosedChartJsonFence(source);

  if (!match) {
    return { source, loadingPlaceholders: [] };
  }

  const placeholder = `CHART_JSON_LOADING_PLACEHOLDER_${Date.now()}`;
  const html = '<div class="chart-json-loading" role="status">Chart is being generated...</div>';

  return {
    source: `${source.slice(0, match.index)}\n${placeholder}\n`,
    loadingPlaceholders: [{ placeholder, html }]
  };
}

function findLastUnclosedChartJsonFence(source) {
  const openingPattern = /^```chart-json[^\n\r]*$/gim;
  let match = null;

  for (const candidate of source.matchAll(openingPattern)) {
    const afterOpening = source.slice(candidate.index + candidate[0].length);
    if (!/^\s*```\s*$/m.test(afterOpening)) {
      match = candidate;
    }
  }

  return match;
}

function createMermaidBlockId(source, env) {
  let hash = 2166136261;

  for (let index = 0; index < source.length; index += 1) {
    hash ^= source.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }

  const baseId = `mermaid-${(hash >>> 0).toString(36)}`;

  if (!env) {
    return baseId;
  }

  env.mermaidBlockCounts ??= new Map();

  const occurrence = env.mermaidBlockCounts.get(baseId) ?? 0;
  env.mermaidBlockCounts.set(baseId, occurrence + 1);

  return occurrence === 0 ? baseId : `${baseId}-${occurrence + 1}`;
}

function preserveHtmlTables(source) {
  const htmlTables = [];
  const markdownSource = source.replace(/<table\b[\s\S]*?<\/table>/gi, (block) => {
    const sanitized = sanitizeTableHtml(block);

    if (!sanitized) {
      return block;
    }

    const placeholder = `HTML_TABLE_PLACEHOLDER_${htmlTables.length}_${Date.now()}`;
    htmlTables.push({ placeholder, html: sanitized });
    return placeholder;
  });

  return { markdownSource, htmlTables };
}

function sanitizeTableHtml(block) {
  const normalized = block.replace(
    /<\s*(\/?)\s*(table|thead|tbody|tr|th|td)\b[^>]*>/gi,
    (_, closing, tag) => `<${closing}${tag.toLowerCase()}>`
  );
  const withoutAllowedTags = normalized.replace(/<\/?(?:table|thead|tbody|tr|th|td)>/gi, "");

  if (/[<>]/.test(withoutAllowedTags)) {
    return null;
  }

  return normalized;
}

function wrapRawChartJson(source) {
  const pattern = /\{"type"\s*:\s*"(bar|pie)"/g;
  const replacements = [];
  let match;

  while ((match = pattern.exec(source)) !== null) {
    if (isInsideCodeFence(source, match.index)) {
      continue;
    }

    const start = match.index;
    let depth = 0;
    let end = start;
    let inString = false;
    let escaped = false;

    for (let i = start; i < source.length; i += 1) {
      const ch = source[i];
      if (escaped) { escaped = false; continue; }
      if (ch === "\\") { escaped = true; continue; }
      if (ch === "\"") { inString = !inString; continue; }
      if (inString) continue;
      if (ch === "{") depth += 1;
      if (ch === "}") {
        depth -= 1;
        if (depth === 0) { end = i + 1; break; }
      }
    }

    const json = source.slice(start, end);
    try {
      JSON.parse(json);
      replacements.push({ start, end, json });
    } catch {
      // ignore unparseable matches
    }
  }

  if (!replacements.length) {
    return { source, promotedBlocks: [] };
  }

  const promotedBlocks = [];
  let result = source;
  for (let i = replacements.length - 1; i >= 0; i -= 1) {
    const { start, end, json } = replacements[i];
    const placeholder = `CHART_JSON_PROMOTED_${Date.now()}_${i}`;
    const html = `<div class="chart-json-block" data-chart-json="${escapeHtml(json)}"></div>`;
    promotedBlocks.push({ placeholder, html });
    result = result.slice(0, start) + `\n${placeholder}\n` + result.slice(end);
  }

  return { source: result, promotedBlocks };
}

function isInsideCodeFence(source, matchIndex) {
  const lines = source.slice(0, matchIndex).split("\n");
  let fenceOpen = false;

  for (const line of lines) {
    if (!fenceOpen && /^```/.test(line)) {
      fenceOpen = true;
    } else if (fenceOpen && /^```\s*$/.test(line)) {
      fenceOpen = false;
    }
  }

  return fenceOpen;
}
