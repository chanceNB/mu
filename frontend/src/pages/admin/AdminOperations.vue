<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  Activity,
  AlertTriangle,
  BarChart3,
  Clock3,
  Database,
  GitBranch,
  RefreshCw,
  ServerCog,
  WalletCards,
} from 'lucide-vue-next'
import { acknowledgeOpsAlert, fetchAnalyticsOverview, fetchPersistedOpsAlerts } from '../../api/analytics'
import { fetchHealth } from '../../api/health'
import MobbinGlassCard from '../../components/mobbin/MobbinGlassCard.vue'
import MobbinHero from '../../components/mobbin/MobbinHero.vue'
import MobbinMetricStrip, { type MobbinMetricItem } from '../../components/mobbin/MobbinMetricStrip.vue'
import MobbinPageShell from '../../components/mobbin/MobbinPageShell.vue'
import MobbinPreviewFrame from '../../components/mobbin/MobbinPreviewFrame.vue'
import MobbinStatusPill from '../../components/mobbin/MobbinStatusPill.vue'
import MobbinTimeline, { type MobbinTimelineItem } from '../../components/mobbin/MobbinTimeline.vue'
import type { AnalyticsOverview, ComponentHealthResponse, HealthResponse, OpsAlertRecord } from '../../types/api'

const health = ref<HealthResponse | null>(null)
const analytics = ref<AnalyticsOverview | null>(null)
const alerts = ref<OpsAlertRecord[]>([])
const acknowledgingAlertId = ref('')
const isLoading = ref(false)
const errorMessage = ref('')

const healthItems = computed(() => {
  if (!health.value) return []
  return [
    { name: '应用服务', component: health.value.application, icon: Activity, note: 'Frontend / Backend app runtime' },
    { name: 'Database', component: health.value.database, icon: Database, note: '持久化数据与业务事务' },
    { name: 'Redis', component: health.value.redis, icon: ServerCog, note: '缓存、队列与短期状态' },
    { name: 'MinIO', component: health.value.minio, icon: ServerCog, note: '课程资料与资源对象存储' },
    { name: 'Model Provider', component: health.value.model, icon: GitBranch, note: 'LLM 调用通道' },
    ...(health.value.vector
      ? [{ name: 'Vector Index', component: health.value.vector, icon: ServerCog, note: 'RAG 检索索引' }]
      : []),
  ].filter((item) => Boolean(item.component))
})

const criticalAlertCount = computed(
  () => alerts.value.filter((alert) => ['ERROR', 'CRITICAL'].includes(alert.severity)).length,
)

const runtimeStats = computed<MobbinMetricItem[]>(() => [
  {
    label: '应用健康',
    value: health.value?.application?.status ?? (isLoading.value ? 'LOADING' : 'UNKNOWN'),
    note: health.value?.application?.detail ?? '等待 /api/health',
  },
  {
    label: 'Token 用量',
    value: analytics.value?.tokenUsage.totalTokens ?? 0,
    note: `${analytics.value?.tokenUsage.promptTokens ?? 0} prompt / ${analytics.value?.tokenUsage.completionTokens ?? 0} completion`,
  },
  {
    label: 'Model 延迟',
    value: health.value?.model?.status ?? 'UNKNOWN',
    note: metadataSummary(health.value?.model),
  },
  {
    label: 'Fallback 率',
    value: alerts.value.some((alert) => alert.alertType.toLowerCase().includes('fallback')) ? 'WATCH' : '0%',
    note: '根据持久化告警上下文推断',
  },
  {
    label: '错误率',
    value: criticalAlertCount.value,
    note: `${alerts.value.length} 条告警已加载`,
  },
  {
    label: '告警数量',
    value: alerts.value.length,
    note: '来自 persisted alerts',
  },
])

const recentTasks = computed(() => [
  {
    name: 'Agent 任务',
    detail: `${analytics.value?.agentTaskCount ?? 0} 条任务记录`,
    status: analytics.value ? 'LIVE' : 'WAITING',
  },
  {
    name: '学习活动',
    detail: `${analytics.value?.learningEventCount ?? 0} 条学习事件 / ${analytics.value?.answerRecordCount ?? 0} 条答案`,
    status: analytics.value ? 'READY' : 'WAITING',
  },
  {
    name: '错题循环',
    detail: `${analytics.value?.wrongQuestionCount ?? 0} 条错题记录`,
    status: analytics.value ? 'READY' : 'WAITING',
  },
])

const modelCalls = computed(() => [
  {
    name: 'Model 调用',
    detail: `${analytics.value?.modelCallCount ?? 0} 条 analytics 调用记录`,
    status: health.value?.model?.status ?? 'UNKNOWN',
  },
  {
    name: 'Token 台账',
    detail: `${analytics.value?.tokenUsage.totalTokens ?? 0} total tokens`,
    status: analytics.value ? 'ACTIVE' : 'WAITING',
  },
  ...Object.entries(analytics.value?.resourceReviewStatusCounts ?? {}).map(([status, count]) => ({
    name: `审核 ${status}`,
    detail: `${count} 个资源`,
    status,
  })),
])

const traceLogItems = computed(() => [
  {
    label: '最近 trace',
    value: `${analytics.value?.agentTaskCount ?? 0} 个 Agent 任务`,
    status: analytics.value ? 'READY' : 'WAITING',
  },
  {
    label: 'Model 调用',
    value: `${analytics.value?.modelCallCount ?? 0} 次调用 / ${analytics.value?.tokenUsage.totalTokens ?? 0} tokens`,
    status: health.value?.model?.status ?? 'UNKNOWN',
  },
  {
    label: '错误上下文',
    value: errorMessage.value || `${alerts.value.length} 条持久化告警`,
    status: errorMessage.value ? 'FAILED' : 'READY',
  },
  {
    label: '告警上下文',
    value: alerts.value[0]?.summary ?? '暂无激活的持久化告警',
    status: alerts.value[0]?.severity ?? 'CLEAR',
  },
])

const alertTimeline = computed<MobbinTimelineItem[]>(() =>
  alerts.value.map((alert) => ({
    title: alert.alertType,
    subtitle: alert.summary,
    detail: `状态 ${alert.status}${alert.acknowledgedBy ? ` / 确认人 ${alert.acknowledgedBy}` : ''}`,
    status: alert.severity,
    time: alert.updatedAt,
  })),
)

onMounted(() => {
  void loadOperations()
})

async function loadOperations() {
  isLoading.value = true
  errorMessage.value = ''
  try {
    const [healthResponse, analyticsResponse, alertsResponse] = await Promise.all([
      fetchHealth(),
      fetchAnalyticsOverview(),
      fetchPersistedOpsAlerts(),
    ])
    health.value = healthResponse
    analytics.value = analyticsResponse && 'tokenUsage' in analyticsResponse ? analyticsResponse : null
    alerts.value = Array.isArray(alertsResponse) ? alertsResponse : []
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '无法加载运维数据'
  } finally {
    isLoading.value = false
  }
}

function metadataSummary(component?: ComponentHealthResponse): string {
  if (!component) return '等待后端健康数据'
  const entries = Object.entries(component.metadata)
  if (entries.length === 0) return component.detail
  return entries.map(([key, value]) => `${key}: ${value}`).join(' / ')
}

async function acknowledgeAlert(alert: OpsAlertRecord) {
  acknowledgingAlertId.value = alert.alertId
  errorMessage.value = ''
  try {
    const updated = await acknowledgeOpsAlert(alert.alertId)
    alerts.value = alerts.value.map((item) => (item.alertId === updated.alertId ? updated : item))
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '无法确认告警'
  } finally {
    acknowledgingAlertId.value = ''
  }
}
</script>

<template>
  <MobbinPageShell aria-label="AI 运行控制中心">
    <MobbinHero
      eyebrow="AI Runtime Command Center"
      title="AI 运行控制中心"
      description="集中查看应用健康、Token 预算、模型调用、Fallback 信号、持久化告警和 Trace 上下文，让运维页面更像实时控制台。"
    >
      <template #actions>
        <button class="mobbin-primary-button" type="button" :disabled="isLoading" @click="loadOperations">
          <RefreshCw :size="18" aria-hidden="true" />
          {{ isLoading ? '正在刷新数据' : '刷新数据' }}
        </button>
      </template>
      <template #preview>
        <MobbinPreviewFrame label="Runtime Snapshot">
          <div class="runtime-snapshot">
            <Activity :size="24" aria-hidden="true" />
            <strong>{{ health?.application?.status ?? 'UNKNOWN' }}</strong>
            <p>{{ health?.application?.detail ?? '等待健康检查结果' }}</p>
            <div>
              <MobbinStatusPill :status="health?.model?.status ?? 'UNKNOWN'">Model {{ health?.model?.status ?? 'UNKNOWN' }}</MobbinStatusPill>
              <MobbinStatusPill :status="alerts.length ? 'WATCH' : 'READY'">{{ alerts.length }} Alerts</MobbinStatusPill>
            </div>
          </div>
        </MobbinPreviewFrame>
      </template>
    </MobbinHero>

    <MobbinMetricStrip :items="runtimeStats" data-test="admin-triage" />

    <p v-if="errorMessage" class="mobbin-error" role="status">{{ errorMessage }}</p>
    <p class="visually-hidden" data-test="status-showcase-admin">管理员运维 Runtime 健康 依赖健康</p>

    <section class="ops-command-grid">
      <MobbinGlassCard eyebrow="Server Status Board" title="依赖健康矩阵" elevated data-test="admin-dependency-matrix">
        <template #icon>
          <ServerCog :size="20" aria-hidden="true" />
        </template>

        <ul v-if="healthItems.length" class="status-board-list">
          <li v-for="item in healthItems" :key="item.name">
            <component :is="item.icon" :size="17" aria-hidden="true" />
            <span :class="['status-dot', item.component.status.toLowerCase()]" />
            <div>
              <strong>{{ item.name }}</strong>
              <p>{{ item.note }}</p>
              <small>{{ item.component.detail }} / {{ metadataSummary(item.component) }}</small>
            </div>
            <MobbinStatusPill :status="item.component.status">{{ item.component.status }}</MobbinStatusPill>
          </li>
        </ul>
        <p v-else class="mobbin-empty">正在从 /api/health 加载 Runtime 依赖健康信号。</p>
      </MobbinGlassCard>

      <MobbinGlassCard eyebrow="Live Alert Timeline" title="告警时间线" elevated data-test="admin-alert-table">
        <template #icon>
          <AlertTriangle :size="20" aria-hidden="true" />
        </template>

        <div v-if="alerts.length" class="alert-timeline-wrap">
          <MobbinTimeline :items="alertTimeline" />
          <div class="alert-actions">
            <button
              v-for="alert in alerts"
              :key="alert.alertId"
              type="button"
              :disabled="alert.status === 'ACKNOWLEDGED' || acknowledgingAlertId === alert.alertId"
              @click="acknowledgeAlert(alert)"
            >
              {{ alert.status === 'ACKNOWLEDGED' ? `${alert.alertType} 已确认` : `确认 ${alert.alertType}` }}
            </button>
          </div>
        </div>
        <p v-else class="mobbin-empty">暂无持久化告警。新的告警会从 /api/analytics/ops/alerts/persisted 加载。</p>
      </MobbinGlassCard>

      <MobbinGlassCard eyebrow="Learning Runtime" title="Agent 与学习活动">
        <template #icon>
          <Clock3 :size="20" aria-hidden="true" />
        </template>
        <ul class="signal-list">
          <li v-for="task in recentTasks" :key="task.name">
            <Activity :size="16" aria-hidden="true" />
            <div>
              <strong>{{ task.name }}</strong>
              <span>{{ task.detail }}</span>
            </div>
            <MobbinStatusPill :status="task.status">{{ task.status }}</MobbinStatusPill>
          </li>
        </ul>
      </MobbinGlassCard>

      <MobbinGlassCard eyebrow="Model Calls" title="Provider 与审核信号">
        <template #icon>
          <WalletCards :size="20" aria-hidden="true" />
        </template>
        <p v-if="!analytics" class="mobbin-empty">正在从 /api/analytics/overview 加载分析概览。</p>
        <ul v-else class="signal-list">
          <li v-for="call in modelCalls" :key="`${call.name}-${call.status}`">
            <BarChart3 :size="16" aria-hidden="true" />
            <div>
              <strong>{{ call.name }}</strong>
              <span>{{ call.detail }}</span>
            </div>
            <MobbinStatusPill :status="call.status">{{ call.status }}</MobbinStatusPill>
          </li>
        </ul>
      </MobbinGlassCard>

      <MobbinGlassCard eyebrow="Trace Context" title="最近 Runtime 上下文">
        <template #icon>
          <GitBranch :size="20" aria-hidden="true" />
        </template>
        <ul class="trace-signal-list">
          <li v-for="item in traceLogItems" :key="item.label">
            <div>
              <strong>{{ item.label }}</strong>
              <MobbinStatusPill :status="item.status">{{ item.status }}</MobbinStatusPill>
            </div>
            <p>{{ item.value }}</p>
          </li>
        </ul>
      </MobbinGlassCard>

      <MobbinGlassCard eyebrow="Developer Contract" title="API 来源" class="api-source-card" data-test="admin-api-sources">
        <template #icon>
          <Database :size="20" aria-hidden="true" />
        </template>
        <ul class="api-source-list">
          <li>GET /api/health</li>
          <li>GET /api/analytics/overview</li>
          <li>GET /api/analytics/ops/alerts/persisted</li>
          <li>POST /api/analytics/ops/alerts/{alertId}/acknowledge</li>
          <li>GET /api/admin/model-providers</li>
          <li>GET /api/agent/tasks/{taskId}/trace</li>
          <li>GET /api/reviews/resources</li>
        </ul>
      </MobbinGlassCard>
    </section>
  </MobbinPageShell>
</template>

<style scoped>
.mobbin-primary-button {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  padding: 10px 14px;
  color: #ffffff;
  font: inherit;
  font-weight: 900;
  background: linear-gradient(135deg, #4f46e5, #2563eb);
  border: 1px solid transparent;
  border-radius: 12px;
  box-shadow: 0 14px 26px rgba(79, 70, 229, 0.22);
  cursor: pointer;
}

.runtime-snapshot {
  display: grid;
  gap: 10px;
}

.runtime-snapshot svg {
  color: #4f46e5;
}

.runtime-snapshot strong {
  color: #0f172a;
  font-size: 26px;
}

.runtime-snapshot p,
.signal-list span,
.trace-signal-list p,
.status-board-list p,
.status-board-list small,
.mobbin-empty {
  color: #64748b;
  font-size: 13px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.runtime-snapshot div {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.ops-command-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 16px;
}

.ops-command-grid > :deep(.mobbin-glass-card) {
  grid-column: span 6;
}

.api-source-card {
  grid-column: span 12 !important;
}

.status-board-list,
.signal-list,
.trace-signal-list,
.api-source-list {
  display: grid;
  gap: 10px;
  padding: 0;
  list-style: none;
}

.status-board-list li,
.signal-list li {
  display: grid;
  grid-template-columns: auto auto minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
}

.status-board-list svg,
.signal-list svg {
  color: #4f46e5;
}

.status-dot {
  width: 10px;
  height: 10px;
  background: #94a3b8;
  border-radius: 999px;
  box-shadow: 0 0 0 4px rgba(148, 163, 184, 0.12);
}

.status-dot.ok,
.status-dot.ready,
.status-dot.active,
.status-dot.up,
.status-dot.healthy {
  background: #10b981;
  box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.14);
}

.status-dot.failed,
.status-dot.error,
.status-dot.down,
.status-dot.critical {
  background: #ef4444;
  box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.14);
}

.status-board-list strong,
.signal-list strong,
.trace-signal-list strong {
  display: block;
  color: #0f172a;
  font-size: 14px;
}

.alert-timeline-wrap {
  display: grid;
  gap: 12px;
}

.alert-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.alert-actions button {
  min-height: 34px;
  padding: 7px 10px;
  color: #4f46e5;
  font: inherit;
  font-size: 12px;
  font-weight: 900;
  background: #eef2ff;
  border: 1px solid #c7d2fe;
  border-radius: 999px;
  cursor: pointer;
}

.trace-signal-list li {
  display: grid;
  gap: 7px;
  min-width: 0;
  padding: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
}

.trace-signal-list li > div {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
}

.api-source-list {
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
}

.api-source-list li {
  padding: 10px 12px;
  color: #475569;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  overflow-wrap: anywhere;
}

.mobbin-empty,
.mobbin-error {
  padding: 13px;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  border-radius: 16px;
}

.mobbin-error {
  color: #b91c1c;
  background: #fef2f2;
  border-color: #fecaca;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 1180px) {
  .ops-command-grid > :deep(.mobbin-glass-card),
  .api-source-card {
    grid-column: span 12 !important;
  }
}

@media (max-width: 680px) {
  .status-board-list li,
  .signal-list li {
    grid-template-columns: 1fr;
  }

  .mobbin-primary-button {
    width: 100%;
  }
}
</style>
