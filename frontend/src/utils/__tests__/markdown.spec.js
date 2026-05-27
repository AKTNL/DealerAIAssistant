import { describe, expect, it } from "vitest";
import { renderMarkdownLite } from "../markdown";

describe("renderMarkdownLite", () => {
  it("adds noopener and noreferrer to rendered links", () => {
    const html = renderMarkdownLite("[OpenAI](https://openai.com)");

    expect(html).toContain('target="_blank"');
    expect(html).toContain('rel="noopener noreferrer"');
  });

  it("preserves html tables in assistant replies", () => {
    const html = renderMarkdownLite(`## Data Support
<table>
<thead>
<tr>
<th>指标</th>
<th>数值</th>
</tr>
</thead>
<tbody>
<tr>
<td>商机总数</td>
<td>2</td>
</tr>
</tbody>
</table>`);

    expect(html).toContain("<table>");
    expect(html).toContain("<th>指标</th>");
    expect(html).not.toContain("&lt;table&gt;");
  });

  it("keeps unsupported html escaped", () => {
    const html = renderMarkdownLite("<div>unsafe</div>");

    expect(html).toContain("&lt;div&gt;unsafe&lt;/div&gt;");
    expect(html).not.toContain("<div>unsafe</div>");
  });

  it("renders mermaid fences as a structured block with chart and source panels", () => {
    const html = renderMarkdownLite("```mermaid\ngraph TD\nA-->B\n```");

    expect(html).toContain('class="mermaid-block"');
    expect(html).toContain('class="mermaid-chart"');
    expect(html).toContain('class="mermaid-source"');
    expect(html).toContain('data-mermaid-block-id="');
  });

  it("uses a stable mermaid block id for the same source", () => {
    const first = renderMarkdownLite("```mermaid\ngraph TD\nA-->B\n```");
    const second = renderMarkdownLite("```mermaid\ngraph TD\nA-->B\n```");
    const firstId = first.match(/data-mermaid-block-id="([^"]+)"/)?.[1];
    const secondId = second.match(/data-mermaid-block-id="([^"]+)"/)?.[1];

    expect(firstId).toBeTruthy();
    expect(secondId).toBe(firstId);
  });

  it("assigns unique mermaid block ids to duplicate charts in the same message", () => {
    const html = renderMarkdownLite(`\`\`\`mermaid
graph TD
A-->B
\`\`\`

\`\`\`mermaid
graph TD
A-->B
\`\`\``);
    const ids = [...html.matchAll(/data-mermaid-block-id="([^"]+)"/g)].map((match) => match[1]);

    expect(ids).toHaveLength(2);
    expect(new Set(ids).size).toBe(2);
  });

  it("renders chart-empty fences as a structured empty chart state", () => {
    const html = renderMarkdownLite(`\`\`\`chart-empty
reason: ALL_ZERO_SIGNAL
title: No visual signal
body: Records exist, but the key metric is 0.
\`\`\``);

    expect(html).toContain('class="analysis-empty-chart"');
    expect(html).toContain('role="status"');
    expect(html).toContain('data-chart-empty-reason="ALL_ZERO_SIGNAL"');
    expect(html).toContain("No visual signal");
    expect(html).toContain("Records exist, but the key metric is 0.");
  });

  it("escapes chart-empty title and body values", () => {
    const html = renderMarkdownLite(`\`\`\`chart-empty
reason: DENOMINATOR_ZERO
title: <img src=x onerror=alert(1)>
body: <script>alert(1)</script>
\`\`\``);

    expect(html).toContain("&lt;img src=x onerror=alert(1)&gt;");
    expect(html).toContain("&lt;script&gt;alert(1)&lt;/script&gt;");
    expect(html).not.toContain("<script>alert(1)</script>");
  });

  it("renders chart-json fences as chart placeholders with escaped raw json", () => {
    const html = renderMarkdownLite(`\`\`\`chart-json
{"type":"bar","title":"<Rank>","categories":["A"],"values":[1]}
\`\`\``);

    expect(html).toContain('class="chart-json-block"');
    expect(html).toContain('data-chart-json="{&quot;type&quot;:&quot;bar&quot;');
    expect(html).toContain("&lt;Rank&gt;");
    expect(html).not.toContain("<Rank>");
  });

  it("renders a loading placeholder for an unclosed chart-json fence", () => {
    const html = renderMarkdownLite(`## Data

\`\`\`chart-json
{"type":"bar","title":"Loading"`);

    expect(html).toContain('class="chart-json-loading"');
    expect(html).toContain("Chart is being generated");
    expect(html).not.toContain('class="chart-json-block"');
  });
});
