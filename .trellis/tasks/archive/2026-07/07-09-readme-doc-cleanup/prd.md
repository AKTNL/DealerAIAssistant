# 修复 README 文档可读性

## Goal

提升项目 README 的日常可读性和测试说明准确性，让本地开发者能清楚启动、测试，并理解准确率题库回归该如何运行。

## Requirements

* 保持 README 的既有结构和内容主线，不重写整份文档。
* 明确说明 README 使用 UTF-8 编码；Windows PowerShell 读取时应指定 `-Encoding UTF8`，避免误以为文件内容损坏。
* 更新“准确率题库回归”说明，把新增的 `AccuracyWorkbookRegressionTest` 纳入推荐回归命令。
* 保留人工抽查题库的说明，但让自动化回归优先级更清楚。

## Acceptance Criteria

* [x] README 中有清晰的编码/乱码排查提示。
* [x] README 中的准确率回归命令包含 `AccuracyWorkbookRegressionTest`。
* [x] 文档改动不改变后端、前端运行行为。
* [x] `git diff --check` 通过。

## Definition of Done

* README 更新完成。
* 运行必要的文档级检查。
* 无需新增或修改业务测试，因为本任务只改文档。

## Technical Approach

对 README 做小范围编辑：新增一个简短“文档编码提示”段落，并更新准确率题库回归命令与说明。实际文件已是 UTF-8；乱码来自未指定编码的 Windows PowerShell 读取方式，因此不进行全文件编码转换。

## Decision (ADR-lite)

**Context**: `Get-Content README.md` 在当前 Windows PowerShell 环境会显示乱码，但 `Get-Content -Encoding UTF8 README.md` 能正常显示中文，文件字节也符合 UTF-8。

**Decision**: 不转换 README 编码，也不添加 BOM；只在文档中说明正确读取方式，并同步自动化测试命令。

**Consequences**: 保持跨平台常见的 UTF-8 文本格式，同时降低 Windows 本地排查成本。

## Out of Scope

* 不重构 README 全文。
* 不调整 CI、Maven、前端构建脚本。
* 不改业务代码或测试逻辑。

## Technical Notes

* `README.md` 已可用 `Get-Content -Raw -Encoding UTF8 README.md` 正常读取。
* `.github/workflows/ci.yml` 后端 CI 已运行 `mvn -B -ntp -Dfrontend.skip=true test`，会覆盖所有后端测试。
* 新增准确率题库回归测试文件为 `backend/src/test/java/com/brand/agentpoc/service/AccuracyWorkbookRegressionTest.java`。
* PowerShell 会把未加引号的逗号分隔 `-Dtest=A,B` 解析成参数列表；README 和 backend quality spec 已改为 `mvn "-D..."` 形式。
