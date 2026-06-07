# Defense Presentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `docs/presentation.html` with a concise 9-slide defense deck for the 星曜汽车 AI 分析助手 project.

**Architecture:** The deck is a single static HTML file with embedded CSS and JavaScript. Slides are plain HTML sections, navigation is handled by a small JavaScript state machine, and print/PDF export is handled by CSS media rules.

**Tech Stack:** HTML, CSS, vanilla JavaScript, local assets from the repository.

---

## File Structure

- Modify: `docs/presentation.html`
  - Owns all slide markup, styling, navigation behavior, and print styles.
  - Must not rely on CDN fonts, remote scripts, or external network assets.
- Reference only: `README.md`, `docs/01-功能清单.md`, `docs/03-数据流向.md`, `docs/04-业务架构.md`, `docs/05-技术架构.md`
  - Used to keep business and technical claims consistent with project documentation.

## Task 1: Replace Deck Content

**Files:**
- Modify: `docs/presentation.html`

- [ ] **Step 1: Replace the current 14-slide deck with 9 slides**

Use these slide sections in order:

1. 封面：项目名称、项目定位、技术栈标签。
2. 项目背景：传统经销商数据分析痛点和项目目标。
3. 功能总览：登录、自然语言对话、SSE 流式回复、Markdown/图表、追问、中英双语。
4. 业务场景：目标达成、商机漏斗、销售跟进、市场活动、经营对标、线索来源。
5. 技术架构：Vue 3 前端、Spring Boot 后端、H2/Excel 数据、可选 OpenAI 兼容模型。
6. 核心数据流：Excel 导入、提问、意图识别、规则分析、SSE 事件、前端渲染。
7. 核心亮点：规则优先、Grounded Reference、防幻觉、SSE 时间线、ECharts/Mermaid。
8. 工程质量：认证、Session Token、API Key、模型地址校验、测试覆盖。
9. 总结与展望：完成内容、项目价值、后续扩展。

- [ ] **Step 2: Use the approved six-scenario wording**

The business scenario slide must use this exact scenario list:

```text
目标达成分析
商机漏斗与转化分析
销售跟进分析
市场活动规划与效果分析
经营对标分析
线索来源与自然流量趋势分析
```

Do not present “门店经营活跃度” as a seventh scenario.

- [ ] **Step 3: Keep quantifiable claims verifiable**

Use these verified figures:

```text
6 个分析场景
6 个核心实体
前端 30 个 spec 测试文件
后端 33 个 Test 文件
规则分析服务约 4,255 行
Git 提交数 184+
```

## Task 2: Implement Navigation And Print Behavior

**Files:**
- Modify: `docs/presentation.html`

- [ ] **Step 1: Add slide navigation state**

Use this JavaScript behavior:

```javascript
let currentSlide = 0;
const slides = Array.from(document.querySelectorAll(".slide"));

function showSlide(index) {
  currentSlide = Math.max(0, Math.min(index, slides.length - 1));
  slides.forEach((slide, slideIndex) => {
    slide.classList.toggle("active", slideIndex === currentSlide);
    slide.classList.toggle("before", slideIndex < currentSlide);
  });
  document.querySelector("[data-current-page]").textContent = String(currentSlide + 1);
  document.querySelector("[data-total-pages]").textContent = String(slides.length);
  document.querySelector("[data-progress]").style.width = `${((currentSlide + 1) / slides.length) * 100}%`;
  document.querySelector("[data-prev]").disabled = currentSlide === 0;
  document.querySelector("[data-next]").disabled = currentSlide === slides.length - 1;
}
```

- [ ] **Step 2: Add keyboard and button handlers**

Use this behavior:

```javascript
document.querySelector("[data-prev]").addEventListener("click", () => showSlide(currentSlide - 1));
document.querySelector("[data-next]").addEventListener("click", () => showSlide(currentSlide + 1));

document.addEventListener("keydown", (event) => {
  if (["ArrowRight", "ArrowDown", " "].includes(event.key)) {
    event.preventDefault();
    showSlide(currentSlide + 1);
  }
  if (["ArrowLeft", "ArrowUp"].includes(event.key)) {
    event.preventDefault();
    showSlide(currentSlide - 1);
  }
});

showSlide(0);
```

- [ ] **Step 3: Add print CSS**

Use CSS rules equivalent to:

```css
@media print {
  body {
    overflow: visible;
    background: #fff;
  }
  .nav,
  .progress-shell {
    display: none;
  }
  .slide {
    position: relative;
    opacity: 1;
    transform: none;
    page-break-after: always;
    break-after: page;
  }
}
```

## Task 3: Verify Static Page Behavior

**Files:**
- Verify: `docs/presentation.html`

- [ ] **Step 1: Check the file has no remote dependencies**

Run:

```powershell
rg -n "https?://|@import|fonts.googleapis|cdn" docs\presentation.html
```

Expected: no matches.

- [ ] **Step 2: Check six-scenario wording**

Run:

```powershell
rg -n "门店经营活跃度|七大|7 大|7个|7 个" docs\presentation.html
```

Expected: no matches.

- [ ] **Step 3: Check deck page count markers**

Run:

```powershell
(Select-String -Path docs\presentation.html -Pattern 'class="slide' | Measure-Object).Count
```

Expected: `9`.

- [ ] **Step 4: Open the page locally**

Run:

```powershell
Start-Process .\docs\presentation.html
```

Expected: browser opens the deck at slide 1. Use arrow keys and buttons to confirm slide navigation.
