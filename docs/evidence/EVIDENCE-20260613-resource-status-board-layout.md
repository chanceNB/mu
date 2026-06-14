# EVIDENCE-20260613-resource-status-board-layout

## 任务

学生端资源审核状态板 CSS 布局修复。

## 变更摘要

- `.resource-status-board` 从继承通用 4 列状态板改为最多 2 列。
- 900px 以下切换为 1 列。
- 状态板内 `.resource-title-row` 改为类型/状态第一行、标题独占第二行，避免 `status-pill` 挤压标题。
- 状态板内资源标题覆盖全局 `overflow-wrap: anywhere`，改为 `break-word` + `word-break: normal`，英文优先按单词换行。
- citation、摘要、blockquote 保持可断行，避免撑破卡片。

## 架构漂移检查

| Check | Status | Notes |
|---|---|---|
| Backend layering | PASS | 未修改后端 |
| Frontend rules | PASS | 仅 CSS 布局修复，未新增 API 调用 |
| Agent / RAG rules | PASS | 未修改 Agent / RAG 行为 |
| Security | PASS | 未新增依赖或敏感信息 |
| API / Database | PASS | 未修改 API 合同或数据库结构 |

## 验证

命令：

```bash
cd frontend && pnpm build
```

结果：

- `vue-tsc -b` 通过。
- `vite build` 通过。
- 输出包含 `dist/index.html`、CSS 和 JS bundle。

## 验收结论

PASS。

- `JOIN duplication walkthrough` 和 `Fix a one-to-many query` 使用状态板局部断词规则，不再受 `.resource-list span { overflow-wrap: anywhere; }` 影响而按字符断开。
- `PENDING_CRITIC` 状态标签位于标题上一行右侧，不再挤压标题文本。
- 宽屏状态板最多 2 列，900px 以下 1 列。
- citation、小段文本、blockquote 使用 `break-word`，可在卡片内换行。
- 未发现会引入横向滚动条的宽度规则。
