const GENERIC_KEYWORDS = [
  "const", "let", "var", "function", "return", "if", "else", "for", "while",
  "switch", "case", "break", "continue", "class", "new", "import", "export",
  "from", "async", "await", "try", "catch", "throw", "public", "private",
  "protected", "static", "void", "boolean", "int", "long", "double", "true",
  "false", "null"
];

export function renderMarkdownLite(source) {
  const lines = String(source ?? "").replace(/\r\n/g, "\n").split("\n");
  const blocks = [];
  let index = 0;

  while (index < lines.length) {
    const currentLine = lines[index];

    if (!currentLine.trim()) {
      index += 1;
      continue;
    }

    if (currentLine.trim().startsWith("```")) {
      const { html, nextIndex } = renderFencedCodeBlock(lines, index);
      blocks.push(html);
      index = nextIndex;
      continue;
    }

    if (isTableStart(lines, index)) {
      const { html, nextIndex } = renderTable(lines, index);
      blocks.push(html);
      index = nextIndex;
      continue;
    }

    if (currentLine.startsWith(">")) {
      const { html, nextIndex } = renderBlockquote(lines, index);
      blocks.push(html);
      index = nextIndex;
      continue;
    }

    if (/^#{1,3}\s+/.test(currentLine)) {
      blocks.push(renderHeading(currentLine));
      index += 1;
      continue;
    }

    if (isListLine(currentLine)) {
      const { html, nextIndex } = renderList(lines, index);
      blocks.push(html);
      index = nextIndex;
      continue;
    }

    const { html, nextIndex } = renderParagraph(lines, index);
    blocks.push(html);
    index = nextIndex;
  }

  return blocks.join("");
}

function renderHeading(line) {
  const level = Math.min(line.match(/^#+/)[0].length, 3);
  const content = formatInline(line.slice(level).trim());
  return `<h${level}>${content}</h${level}>`;
}

function renderParagraph(lines, startIndex) {
  const buffer = [];
  let index = startIndex;

  while (index < lines.length) {
    const line = lines[index];
    if (!line.trim()) {
      break;
    }
    if (line.trim().startsWith("```") || /^#{1,3}\s+/.test(line) || line.startsWith(">") || isListLine(line) || isTableStart(lines, index)) {
      break;
    }

    buffer.push(line.trim());
    index += 1;
  }

  const content = formatInline(buffer.join("\n")).replace(/\n/g, "<br />");
  return {
    html: `<p>${content}</p>`,
    nextIndex: index
  };
}

function renderList(lines, startIndex) {
  const firstLine = lines[startIndex];
  const ordered = /^\s*\d+\.\s+/.test(firstLine);
  const tag = ordered ? "ol" : "ul";
  const items = [];
  let index = startIndex;

  while (index < lines.length && isListLine(lines[index])) {
    const itemText = lines[index]
      .replace(/^\s*(?:[-*]|\d+\.)\s+/, "")
      .trim();
    items.push(`<li>${formatInline(itemText)}</li>`);
    index += 1;
  }

  return {
    html: `<${tag}>${items.join("")}</${tag}>`,
    nextIndex: index
  };
}

function renderBlockquote(lines, startIndex) {
  const buffer = [];
  let index = startIndex;

  while (index < lines.length && lines[index].startsWith(">")) {
    buffer.push(lines[index].replace(/^>\s?/, ""));
    index += 1;
  }

  const content = formatInline(buffer.join("\n")).replace(/\n/g, "<br />");
  return {
    html: `<blockquote><p>${content}</p></blockquote>`,
    nextIndex: index
  };
}

function renderFencedCodeBlock(lines, startIndex) {
  const fenceLine = lines[startIndex].trim();
  const language = fenceLine.slice(3).trim().toLowerCase();
  const buffer = [];
  let index = startIndex + 1;

  while (index < lines.length && !lines[index].trim().startsWith("```")) {
    buffer.push(lines[index]);
    index += 1;
  }

  const code = buffer.join("\n");
  const highlighted = highlightCode(code, language);
  const languageClass = language ? ` language-${escapeHtml(language)}` : "";

  return {
    html: `<pre><code class="${languageClass.trim()}">${highlighted}</code></pre>`,
    nextIndex: Math.min(index + 1, lines.length)
  };
}

function renderTable(lines, startIndex) {
  const headerCells = splitTableRow(lines[startIndex]);
  const rows = [];
  let index = startIndex + 2;

  while (index < lines.length && looksLikeTableRow(lines[index])) {
    rows.push(splitTableRow(lines[index]));
    index += 1;
  }

  const headerHtml = headerCells.map((cell) => `<th>${formatInline(cell)}</th>`).join("");
  const bodyHtml = rows
    .map((row) => `<tr>${row.map((cell) => `<td>${formatInline(cell)}</td>`).join("")}</tr>`)
    .join("");

  return {
    html: `<table><thead><tr>${headerHtml}</tr></thead><tbody>${bodyHtml}</tbody></table>`,
    nextIndex: index
  };
}

function isTableStart(lines, index) {
  return looksLikeTableRow(lines[index]) && looksLikeTableSeparator(lines[index + 1] ?? "");
}

function looksLikeTableRow(line) {
  return line.includes("|") && splitTableRow(line).length > 1;
}

function looksLikeTableSeparator(line) {
  return /^\s*\|?(?:\s*:?-{3,}:?\s*\|)+\s*:?-{3,}:?\s*\|?\s*$/.test(line);
}

function splitTableRow(line) {
  return line
    .trim()
    .replace(/^\|/, "")
    .replace(/\|$/, "")
    .split("|")
    .map((cell) => cell.trim());
}

function isListLine(line) {
  return /^\s*(?:[-*]|\d+\.)\s+/.test(line);
}

function formatInline(text) {
  const safe = escapeHtml(text ?? "");
  const codeTokens = [];
  let html = safe.replace(/`([^`]+)`/g, (_, code) => {
    const token = `__CODE_TOKEN_${codeTokens.length}__`;
    codeTokens.push(`<code>${code}</code>`);
    return token;
  });

  html = html
    .replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noreferrer">$1</a>')
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/\*([^*]+)\*/g, "<em>$1</em>");

  return codeTokens.reduce((result, token, tokenIndex) => {
    return result.replace(`__CODE_TOKEN_${tokenIndex}__`, token);
  }, html);
}

function highlightCode(code, language) {
  const safe = escapeHtml(code);

  if (!language) {
    return safe;
  }

  if (["json"].includes(language)) {
    return highlightJson(safe);
  }

  if (["html", "xml", "vue"].includes(language)) {
    return highlightMarkup(safe);
  }

  if (["bash", "sh", "shell", "powershell", "ps1"].includes(language)) {
    return highlightShell(safe);
  }

  if (["js", "jsx", "ts", "tsx", "java", "kotlin", "c", "cpp", "cs"].includes(language)) {
    return highlightGenericCode(safe, GENERIC_KEYWORDS);
  }

  return safe;
}

function highlightJson(code) {
  let html = code.replace(
    /(&quot;.*?&quot;)(\s*:)/g,
    '<span class="token-attr-name">$1</span>$2'
  );
  html = html.replace(
    /:\s*(&quot;.*?&quot;)/g,
    ': <span class="token-string">$1</span>'
  );
  html = html.replace(/\b(true|false|null)\b/g, '<span class="token-keyword">$1</span>');
  html = html.replace(/\b\d+(?:\.\d+)?\b/g, '<span class="token-number">$&</span>');
  return html;
}

function highlightMarkup(code) {
  return code.replace(
    /(&lt;\/?)([\w:-]+)(.*?)(\/?&gt;)/g,
    (_, start, tagName, attrs, end) => {
      const highlightedAttrs = attrs.replace(
        /([\w:-]+)=(&quot;.*?&quot;)/g,
        '<span class="token-attr-name">$1</span>=<span class="token-attr-value">$2</span>'
      );
      return `${start}<span class="token-tag">${tagName}</span>${highlightedAttrs}${end}`;
    }
  );
}

function highlightShell(code) {
  const tokenStore = createTokenStore();
  let html = tokenStore.wrap(code, /(^|\s)(#.*)$/gm, "token-comment");
  html = tokenStore.wrap(html, /(&quot;.*?&quot;|&#39;.*?&#39;)/g, "token-string");
  html = tokenStore.wrap(html, /(\$[A-Za-z_][A-Za-z0-9_]*)/g, "token-variable");
  html = html.replace(/\s(-{1,2}[A-Za-z-]+)/g, ' <span class="token-keyword">$1</span>');
  return tokenStore.restore(html);
}

function highlightGenericCode(code, keywords) {
  const tokenStore = createTokenStore();
  let html = tokenStore.wrap(code, /(\/\/.*$|\/\*[\s\S]*?\*\/)/gm, "token-comment");
  html = tokenStore.wrap(html, /(&quot;.*?&quot;|&#39;.*?&#39;)/g, "token-string");
  html = tokenStore.wrap(html, /(@[A-Za-z_][A-Za-z0-9_]*)/g, "token-variable");
  html = html.replace(/\b\d+(?:\.\d+)?\b/g, '<span class="token-number">$&</span>');

  const keywordPattern = new RegExp(`\\b(${keywords.join("|")})\\b`, "g");
  html = html.replace(keywordPattern, '<span class="token-keyword">$1</span>');

  return tokenStore.restore(html);
}

function createTokenStore() {
  const tokens = [];

  return {
    wrap(input, pattern, className) {
      return input.replace(pattern, (match) => {
        const placeholder = `__TOK${toAlphaToken(tokens.length)}__`;
        tokens.push({
          placeholder,
          html: `<span class="${className}">${match}</span>`
        });
        return placeholder;
      });
    },
    restore(input) {
      return tokens.reduce((html, token) => {
        return html.replace(token.placeholder, token.html);
      }, input);
    }
  };
}

function toAlphaToken(index) {
  let current = index + 1;
  let token = "";

  while (current > 0) {
    current -= 1;
    token = String.fromCharCode(65 + (current % 26)) + token;
    current = Math.floor(current / 26);
  }

  return token;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}
