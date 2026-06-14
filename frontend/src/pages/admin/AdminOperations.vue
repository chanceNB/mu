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
    { name: '应用服务', component: health.value.application, icon: Activity },
    { name: 'Database', component: health.value.database, icon: Database },
    { name: 'Redis', component: health.value.redis, icon: ServerCog },
    { name: 'MinIO', component: health.value.minio, icon: ServerCog },
    { name: 'Model Provider', component: health.value.model, icon: GitBranch },
    ...(health.value.vector
      ? [{ name: 'Vector 索引', component: health.value.vector, icon: ServerCog }]
      : []),
  ]
})

const runtimeStats = computed(() => [
  {
    label: 'RAG 索引队列',
    value: health.value?.vector?.status ?? health.value?.database.status ?? (isLoading.value ? 'LOADING' : 'UNKNOWN'),
    note: health.value?.vector ? metadataSummary(health.value.vector) : 'Vector 队列根据健康信号推断。',
  },
  {
    label: 'Token 用量',
    value: String(analytics.value?.tokenUsage.totalTokens ?? 0),
    note: `${analytics.value?.tokenUsage.promptTokens ?? 0} prompt / ${analytics.value?.tokenUsage.completionTokens ?? 0} completion`,
  },
  {
    label: 'Model 延迟',
    value: health.value?.model.status ?? 'UNKNOWN',
    note: metadataSummary(health.value?.model),
  },
  {
    label: 'Fallback 率',
    value: alerts.value.some((alert) => alert.alertType.toLowerCase().includes('fallback')) ? 'WATCH' : '0%',
    note: '在专用指标接入前，根据持久化告警上下文推导。',
  },
  {
    label: '错误率',
    value: alerts.value.filter((alert) => alert.severity === 'ERROR' || alert.severity === 'CRITICAL').length,
    note: `${alerts.value.length} 条持久化告警已加载`,
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
    status: health.value?.model.status ?? 'UNKNOWN',
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
    status: health.value?.model.status ?? 'UNKNOWN',
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
    analytics.value = analyticsResponse
    alerts.value = alertsResponse
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '无法加载运维数据'
  } finally {
    isLoading.value = false
  }
}

function metadataSummary(component?: ComponentHealthResponse): string {
  if (!component) return '等待后端健康数据。'
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
  <section class="workspace secondary-workspace" aria-label="管理员运维">
    <header class="workspace-header secondary-page-header">
      <div>
        <p class="eyebrow">运维</p>
        <h2>管理员运维</h2>
        <p class="header-note">
          Runtime 健康、Token 活动、审核积压和持久化告警会集中展示在一个紧凑的 AI Runtime 控制台中。
        </p>
      </div>
      <button class="primary-action" type="button" :disabled="isLoading" @click="loadOperations">
        <RefreshCw :size="18" aria-hidden="true" />
        {{ isLoading ? '正在刷新数据' : '刷新数据' }}
      </button>
    </header>

    <section class="summary-strip admin-triage ops-metric-grid" data-test="admin-triage">
      <article v-for="stat in runtimeStats" :key="stat.label">
        <span>{{ stat.label }}</span>
        <strong>{{ stat.value }}</strong>
        <p>{{ stat.note }}</p>
      </article>
    </section>

    <section class="ops-console-grid">
      <article class="panel admin-health-panel triage-panel" data-test="admin-dependency-matrix">
        <div class="panel-heading">
          <div>
            <p class="eyebrow">Runtime 依赖</p>
            <h3>健康矩阵</h3>
          </div>
          <ServerCog :size="20" aria-hidden="true" />
        </div>
        <p v-if="errorMessage" class="error-text" role="status">{{ errorMessage }}</p>
        <ul v-if="healthItems.length" class="document-list">
          <li v-for="item in healthItems" :key="item.name">
            <component :is="item.icon" :size="16" aria-hidden="true" />
            <div>
              <strong>{{ item.name }}</strong>
              <span>{{ item.component.detail }} / {{ metadataSummary(item.component) }}</span>
            </div>
            <em :class="['status-pill', item.component.status.toLowerCase()]">{{ item.component.status }}</em>
          </li>
        </ul>
        <p v-else class="answer-text">正在从 /api/health 加载 Runtime 依赖健康信息。</p>
      </article>

      <article class="panel">
        <div class="panel-heading">
          <div>
            <p class="eyebrow">最近任务</p>
            <h3>学习与 Agent 活动</h3>
          </div>
          <Activity :size="20" aria-hidden="true" />
        </div>
        <ul class="document-list">
          <li v-for="task in recentTasks" :key="task.name">
            <Clock3 :size="16" aria-hidden="true" />
            <div>
              <strong>{{ task.name }}</strong>
              <span>{{ task.detail }}</span>
            </div>
            <em :class="['status-pill', task.status.toLowerCase()]">{{ task.status }}</em>
          </li>
        </ul>
      </article>

      <article class="panel admin-alert-panel" data-test="admin-alert-table">
        <div class="panel-heading">
          <div>
            <p class="eyebrow">最近告警</p>
            <h3>持久化告警上下文</h3>
          </div>
          <AlertTriangle :size="20" aria-hidden="true" />
        </div>
        <div v-if="alerts.length" class="alert-table ops-alert-table">
          <div v-for="alert in alerts" :key="alert.alertId">
            <strong>{{ alert.severity }}</strong>
            <span>{{ alert.alertType }}</span>
            <span>{{ alert.summary }}</span>
            <button
              type="button"
              :disabled="alert.status === 'ACKNOWLEDGED' || acknowledgingAlertId === alert.alertId"
              @click="acknowledgeAlert(alert)"
            >
              {{ alert.status === 'ACKNOWLEDGED' ? '已确认' : '确认' }}
            </button>
          </div>
        </div>
        <p v-else class="answer-text">暂无持久化告警。新的告警会从 /api/analytics/ops/alerts/persisted 加载。</p>
      </article>

      <article class="panel">
        <div class="panel-heading">
          <div>
            <p class="eyebrow">Model 调用</p>
            <h3>Provider 与审核信号</h3>
          </div>
          <WalletCards :size="20" aria-hidden="true" />
        </div>
        <p v-if="!analytics" class="answer-text">正在从 /api/analytics/overview 加载分析概览。</p>
        <ul v-else class="document-list">
          <li v-for="call in modelCalls" :key="`${call.name}-${call.status}`">
            <BarChart3 :size="16" aria-hidden="true" />
            <div>
              <strong>{{ call.name }}</strong>
              <span>{{ call.detail }}</span>
            </div>
            <em :class="['status-pill', call.status.toLowerCase()]">{{ call.status }}</em>
          </li>
        </ul>
      </article>

      <article class="panel chart-panel">
        <div class="panel-heading">
          <div>
            <p class="eyebrow">TRACE / 日志</p>
            <h3>最近 Runtime 上下文</h3>
          </div>
          <GitBranch :size="20" aria-hidden="true" />
        </div>
        <ul class="trace-list">
          <li v-for="item in traceLogItems" :key="item.label">
            <div>
              <strong>{{ item.label }}</strong>
              <em :class="['status-pill', item.status.toLowerCase()]">{{ item.status }}</em>
            </div>
            <p>{{ item.value }}</p>
          </li>
        </ul>
      </article>

      <article class="panel admin-api-panel" data-test="admin-api-sources">
        <div class="panel-heading">
          <div>
            <p class="eyebrow">API 来源</p>
            <h3>当前页面契约</h3>
          </div>
          <Database :size="20" aria-hidden="true" />
        </div>
        <ul class="api-source-list expanded">
          <li>GET /api/health</li>
          <li>GET /api/analytics/overview</li>
          <li>GET /api/analytics/ops/alerts/persisted</li>
          <li>POST /api/analytics/ops/alerts/{alertId}/acknowledge</li>
          <li>GET /api/admin/model-providers</li>
          <li>GET /api/agent/tasks/{taskId}/trace</li>
          <li>GET /api/reviews/resources</li>
        </ul>
      </article>
      <article class="panel status-showcase" data-test="status-showcase-admin">
        <div class="panel-heading">
          <div>
            <p class="eyebrow">可用信号</p>
            <h3>Runtime 健康覆盖</h3>
          </div>
          <Activity :size="20" aria-hidden="true" />
        </div>
        <div class="state-token-grid">
          <span class="state-token approved">依赖健康</span>
          <span class="state-token check">Token 活动</span>
          <span class="state-token pending">审核积压</span>
          <span class="state-token empty">学习活动</span>
          <span class="state-token approved">分析概览计数</span>
        </div>
      </article>
    </section>
  </section>
</template>
