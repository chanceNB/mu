# TASK-20260613-resource-status-board-layout

## 目标

修复学生端资源审核状态板中英文资源标题被挤压成一列一字母显示的问题。

## 范围

- 任务类型：Bug fix / Frontend CSS layout
- 任务规模：S
- GitHub research：不需要，属于本项目 CSS 局部布局修复
- 子代理：不启用，小范围单文件样式修复

## Skill Selection

- `vue3-component-design`：用于确认 Vue 页面结构和样式作用范围。
- `dashboard-visualization`：用于状态板卡片布局和响应式列数调整。
- `architecture-drift-check`：用于确认不触碰后端、API、数据库和依赖边界。
- `test-generator`：用于选择前端构建验证命令。
- `changelog-writer`：用于记录本次小修。

Missing skills：无。

## Context Pack

相关记忆和规范：

- `AGENTS.md`
- `docs/memory/PROJECT_MEMORY.md`
- `docs/memory/FRONTEND_MEMORY.md`
- `docs/architecture/ARCHITECTURE_BASELINE.md`
- `docs/architecture/ARCHITECTURE_DRIFT_CHECK.md`
- `docs/harness/TEST_COMMANDS.md`

允许修改：

- `frontend/src/style.css`
- `docs/tasks/TASK-20260613-resource-status-board-layout.md`
- `docs/evidence/EVIDENCE-20260613-resource-status-board-layout.md`
- `docs/changelog/CHANGELOG.md`
- `docs/memory/PROJECT_MEMORY.md`
- `docs/memory/FRONTEND_MEMORY.md`

不允许修改：

- 后端接口、DTO、数据库结构
- `frontend/src/pages/student/StudentDashboard.vue` 的 Vue 逻辑
- 新增依赖

## 实施要点

- 将 `.resource-status-board` 固定为最多 2 列。
- 小于 900px 时切换为 1 列。
- 状态板内 `.resource-title-row` 使用标题列和状态标签列，避免 `status-pill` 挤压标题。
- 覆盖状态板内资源标题的 `overflow-wrap: anywhere`，改为按单词正常换行。
- citation、摘要、blockquote 保持 `break-word`，避免撑破卡片。

## 验收标准

- `JOIN duplication walkthrough` 不再一字母一行。
- `Fix a one-to-many query` 不再一字母一行。
- `PENDING_CRITIC` 状态标签不挤压标题。
- 宽屏 2 列，900px 以下 1 列。
- `database-course.md p.12` / `database-course.md p.14` 不撑破卡片。
- 页面无明显横向滚动条。
- `pnpm build` 通过，或说明失败原因。
