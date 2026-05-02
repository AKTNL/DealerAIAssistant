function escapeHtml(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;")
    .replaceAll("'", "&#39;");
}

function formatInline(text) {
  return text
    .replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/\*([^*]+)\*/g, "<em>$1</em>");
}

export function renderMarkdownLite(source) {
  const safe = escapeHtml(source ?? "");
  const blocks = safe.split(/\n{2,}/).map((block) => block.trim()).filter(Boolean);

  return blocks
    .map((block) => {
      if (block.startsWith("### ")) {
        return `<h3>${formatInline(block.slice(4))}</h3>`;
      }
      if (block.startsWith("## ")) {
        return `<h2>${formatInline(block.slice(3))}</h2>`;
      }
      if (block.startsWith("# ")) {
        return `<h1>${formatInline(block.slice(2))}</h1>`;
      }
      if (block.startsWith("- ")) {
        const items = block
          .split(/\r?\n/)
          .map((line) => line.replace(/^- /, "").trim())
          .filter(Boolean)
          .map((item) => `<li>${formatInline(item)}</li>`)
          .join("");
        return `<ul>${items}</ul>`;
      }
      if (/^\d+\.\s/.test(block)) {
        const items = block
          .split(/\r?\n/)
          .map((line) => line.replace(/^\d+\.\s/, "").trim())
          .filter(Boolean)
          .map((item) => `<li>${formatInline(item)}</li>`)
          .join("");
        return `<ol>${items}</ol>`;
      }
      if (block.startsWith("```") && block.endsWith("```")) {
        const lines = block.split(/\r?\n/);
        const code = lines.slice(1, -1).join("\n");
        return `<pre><code>${code}</code></pre>`;
      }
      return `<p>${formatInline(block).replace(/\r?\n/g, "<br />")}</p>`;
    })
    .join("");
}
