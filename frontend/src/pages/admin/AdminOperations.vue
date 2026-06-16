<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  Activity,
  AlertTriangle,
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
import MobbinMetricStrip, { type MobbinMetricItem } from '../../components/mobbin/MobbinMetricStrip.vue'
import MobbinPageShell from '../../components/mobbin/MobbinPageShell.vue'
import MobbinStatusPill from '../../components/mobbin/MobbinStatusPill.vue'
import MobbinTimeline, { type MobbinTimelineItem } from '../../components/mobbin/MobbinTimeline.vue'
import type { AnalyticsOverview, ComponentHealthResponse, HealthResponse, OpsAlertRecord } from '../../types/api'
import '../../components/mobbin/console-layout.css'

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
    <section class="console-page">
      <header class="console-header">
        <div class="console-heading">
          <span class="console-eyebrow">Operations Console</span>
          <h1>AI 运行控制中心</h1>
          <p>集中查看运行健康、依赖状态、告警确认、Agent 活动、模型调用和 API 来源。</p>
        </div>
        <div class="console-actions">
          <button class="console-button" type="button" :disabled="isLoading" @click="loadOperations">
            <RefreshCw :size="18" aria-hidden="true" />
            {{ isLoading ? '正在刷新数据' : '刷新数据' }}
          </button>
        </div>
      </header>

      <MobbinMetricStrip :items="runtimeStats" data-test="admin-triage" />

      <p v-if="errorMessage" class="console-error" role="status">{{ errorMessage }}</p>
      <p class="visually-hidden" data-test="status-showcase-admin">管理员运维 Runtime 健康 依赖健康</p>

      <section class="console-grid">
        <MobbinGlassCard eyebrow="Runtime" title="Runtime 快照" elevated class="console-span-4">
          <template #icon>
            <span class="console-card-icon"><Activity :size="18" aria-hidden="true" /></span>
          </template>
          <div class="runtime-snapshot">
            <strong>{{ health?.application?.status ?? 'UNKNOWN' }}</strong>
            <p>{{ health?.application?.detail ?? '等待健康检查结果。' }}</p>
            <div class="pill-row">
              <MobbinStatusPill :status="health?.model?.status ?? 'UNKNOWN'">Model {{ health?.model?.status ?? 'UNKNOWN' }}</MobbinStatusPill>
              <MobbinStatusPill :status="alerts.length ? 'WATCH' : 'READY'">{{ alerts.length }} Alerts</MobbinStatusPill>
            </div>
          </div>
        </MobbinGlassCard>

        <MobbinGlassCard eyebrow="Dependencies" title="依赖健康" elevated class="console-span-8" data-test="admin-dependency-matrix">
          <template #icon>
            <span class="console-card-icon"><ServerCog :size="18" aria-hidden="true" /></span>
          </template>
          <ul v-if="healthItems.length" class="console-list dependency-board">
            <li v-for="item in healthItems" :key="item.name" class="console-list-item dependency-item">
              <component :is="item.icon" :size="16" aria-hidden="true" />
              <div>
                <strong>{{ item.name }}</strong>
                <p>{{ item.note }}</p>
                <span>{{ item.component.detail }} / {{ metadataSummary(item.component) }}</span>
              </div>
              <MobbinStatusPill :status="item.component.status">{{ item.component.status }}</MobbinStatusPill>
            </li>
          </ul>
          <p v-else class="console-empty">正在从 /api/health 加载 Runtime 依赖健康信号。</p>
        </MobbinGlassCard>

        <MobbinGlassCard eyebrow="Alerts" title="告警中心" class="console-span-5" data-test="admin-alert-table">
          <template #icon>
            <span class="console-card-icon"><AlertTriangle :size="18" aria-hidden="true" /></span>
          </template>
          <template v-if="alerts.length">
            <MobbinTimeline :items="alertTimeline" />
            <ul class="console-list alert-list">
              <li v-for="alert in alerts" :key="alert.alertId" class="console-list-item alert-item">
                <div>
                  <strong>{{ alert.alertType }}</strong>
                  <p>{{ alert.summary }}</p>
                  <span>{{ alert.updatedAt }}</span>
                </div>
                <MobbinStatusPill :status="alert.severity">{{ alert.status }}</MobbinStatusPill>
                <button
                  class="console-button secondary compact-button"
                  type="button"
                  :disabled="alert.status === 'ACKNOWLEDGED' || acknowledgingAlertId === alert.alertId"
                  @click="acknowledgeAlert(alert)"
                >
                  {{ alert.status === 'ACKNOWLEDGED' ? '已确认' : '确认告警' }}
                </button>
              </li>
            </ul>
          </template>
          <p v-else class="console-empty">暂无持久化告警。</p>
        </MobbinGlassCard>

        <MobbinGlassCard eyebrow="Agents" title="Agent 活动" class="console-span-7">
          <template #icon>
            <span class="console-card-icon"><Clock3 :size="18" aria-hidden="true" /></span>
          </template>
          <ul class="console-list">
            <li v-for="task in recentTasks" :key="task.name" class="console-list-item signal-item">
              <div>
                <strong>{{ task.name }}</strong>
                <span>{{ task.detail }}</span>
              </div>
              <MobbinStatusPill :status="task.status">{{ task.status }}</MobbinStatusPill>
            </li>
          </ul>
        </MobbinGlassCard>

        <MobbinGlassCard eyebrow="Models" title="模型调用" class="console-span-6">
          <template #icon>
            <span class="console-card-icon"><WalletCards :size="18" aria-hidden="true" /></span>
          </template>
          <ul v-if="analytics" class="console-list">
            <li v-for="call in modelCalls" :key="call.name + call.status" class="console-list-item signal-item">
              <div>
                <strong>{{ call.name }}</strong>
                <span>{{ call.detail }}</span>
              </div>
              <MobbinStatusPill :status="call.status">{{ call.status }}</MobbinStatusPill>
            </li>
          </ul>
          <p v-else class="console-empty">正在从 /api/analytics/overview 加载分析概览。</p>
        </MobbinGlassCard>

        <MobbinGlassCard eyebrow="Signals" title="审核与 Provider 信号" class="console-span-6">
          <template #icon>
            <span class="console-card-icon"><GitBranch :size="18" aria-hidden="true" /></span>
          </template>
          <ul class="console-list">
            <li class="console-list-item signal-item">
              <div>
                <strong>Provider</strong>
                <span>{{ metadataSummary(health?.model) }}</span>
              </div>
              <MobbinStatusPill :status="health?.model?.status ?? 'UNKNOWN'">{{ health?.model?.status ?? 'UNKNOWN' }}</MobbinStatusPill>
            </li>
            <li v-for="item in traceLogItems" :key="item.label" class="console-list-item signal-item">
              <div>
                <strong>{{ item.label }}</strong>
                <span>{{ item.value }}</span>
              </div>
              <MobbinStatusPill :status="item.status">{{ item.status }}</MobbinStatusPill>
            </li>
          </ul>
        </MobbinGlassCard>

        <MobbinGlassCard eyebrow="API Contract" title="API 来源" class="console-span-12" data-test="admin-api-sources">
          <template #icon>
            <span class="console-card-icon"><Database :size="18" aria-hidden="true" /></span>
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
    </section>
  </MobbinPageShell>
</template>

<style scoped>
.runtime-snapshot {
  display: grid;
  gap: 12px;
}

.runtime-snapshot strong {
  color: #0f172a;
  font-size: 30px;
  line-height: 1.1;
  letter-spacing: 0;
}

.runtime-snapshot p,
.dependency-item p,
.dependency-item span,
.alert-item p,
.alert-item span,
.signal-item span {
  color: #64748b;
  font-size: 13px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.pill-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.dependency-board {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.dependency-item,
.signal-item {
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
}

.dependency-item svg {
  color: #4f46e5;
}

.dependency-item strong,
.alert-item strong,
.signal-item strong {
  color: #0f172a;
  font-size: 14px;
  overflow-wrap: anywhere;
}

.alert-list {
  margin-top: 4px;
}

.alert-item {
  grid-template-columns: minmax(0, 1fr) auto;
}

.compact-button {
  grid-column: 1 / -1;
  justify-self: start;
  min-height: 34px;
  padding: 7px 11px;
  font-size: 12px;
}

.api-source-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 10px;
  padding: 0;
  margin: 0;
  list-style: none;
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

@media (max-width: 900px) {
  .dependency-board {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .dependency-item,
  .signal-item,
  .alert-item {
    grid-template-columns: 1fr;
  }
}
</style>
