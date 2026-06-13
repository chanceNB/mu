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
    { name: 'Application', component: health.value.application, icon: Activity },
    { name: 'Database', component: health.value.database, icon: Database },
    { name: 'Redis', component: health.value.redis, icon: ServerCog },
    { name: 'MinIO', component: health.value.minio, icon: ServerCog },
    { name: 'Model provider', component: health.value.model, icon: GitBranch },
    ...(health.value.vector
      ? [{ name: 'Vector index', component: health.value.vector, icon: ServerCog }]
      : []),
  ]
})

const runtimeStats = computed(() => [
  {
    label: 'RAG Index Queue',
    value: health.value?.vector?.status ?? health.value?.database.status ?? (isLoading.value ? 'LOADING' : 'UNKNOWN'),
    note: health.value?.vector ? metadataSummary(health.value.vector) : 'Vector queue inferred from health signals.',
  },
  {
    label: 'Token usage',
    value: String(analytics.value?.tokenUsage.totalTokens ?? 0),
    note: `${analytics.value?.tokenUsage.promptTokens ?? 0} prompt / ${analytics.value?.tokenUsage.completionTokens ?? 0} completion`,
  },
  {
    label: 'Model Latency',
    value: health.value?.model.status ?? 'UNKNOWN',
    note: metadataSummary(health.value?.model),
  },
  {
    label: 'Fallback Rate',
    value: alerts.value.some((alert) => alert.alertType.toLowerCase().includes('fallback')) ? 'WATCH' : '0%',
    note: 'Derived from persisted alert context until a dedicated metric exists.',
  },
  {
    label: 'Error Rate',
    value: alerts.value.filter((alert) => alert.severity === 'ERROR' || alert.severity === 'CRITICAL').length,
    note: `${alerts.value.length} persisted alerts loaded`,
  },
])

const recentTasks = computed(() => [
  {
    name: 'Agent tasks',
    detail: `${analytics.value?.agentTaskCount ?? 0} tasks recorded`,
    status: analytics.value ? 'LIVE' : 'WAITING',
  },
  {
    name: 'Learning activity',
    detail: `${analytics.value?.learningEventCount ?? 0} learning events / ${analytics.value?.answerRecordCount ?? 0} answers`,
    status: analytics.value ? 'READY' : 'WAITING',
  },
  {
    name: 'Wrong question loop',
    detail: `${analytics.value?.wrongQuestionCount ?? 0} wrong-question records`,
    status: analytics.value ? 'READY' : 'WAITING',
  },
])

const modelCalls = computed(() => [
  {
    name: 'Model calls',
    detail: `${analytics.value?.modelCallCount ?? 0} calls logged by analytics`,
    status: health.value?.model.status ?? 'UNKNOWN',
  },
  {
    name: 'Token ledger',
    detail: `${analytics.value?.tokenUsage.totalTokens ?? 0} total tokens`,
    status: analytics.value ? 'ACTIVE' : 'WAITING',
  },
  ...Object.entries(analytics.value?.resourceReviewStatusCounts ?? {}).map(([status, count]) => ({
    name: `Review ${status}`,
    detail: `${count} resources`,
    status,
  })),
])

const traceLogItems = computed(() => [
  {
    label: 'Recent trace',
    value: `${analytics.value?.agentTaskCount ?? 0} agent tasks`,
    status: analytics.value ? 'READY' : 'WAITING',
  },
  {
    label: 'Model call',
    value: `${analytics.value?.modelCallCount ?? 0} calls / ${analytics.value?.tokenUsage.totalTokens ?? 0} tokens`,
    status: health.value?.model.status ?? 'UNKNOWN',
  },
  {
    label: 'Error context',
    value: errorMessage.value || `${alerts.value.length} persisted alerts`,
    status: errorMessage.value ? 'FAILED' : 'READY',
  },
  {
    label: 'Alert context',
    value: alerts.value[0]?.summary ?? 'No active persisted alerts',
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
    errorMessage.value = error instanceof Error ? error.message : 'Unable to load operations data'
  } finally {
    isLoading.value = false
  }
}

function metadataSummary(component?: ComponentHealthResponse): string {
  if (!component) return 'Waiting for backend health data.'
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
    errorMessage.value = error instanceof Error ? error.message : 'Unable to acknowledge alert'
  } finally {
    acknowledgingAlertId.value = ''
  }
}
</script>

<template>
  <section class="workspace secondary-workspace" aria-label="Admin operations">
    <header class="workspace-header secondary-page-header">
      <div>
        <p class="eyebrow">Operations</p>
        <h2>Admin Operations</h2>
        <p class="header-note">
          Runtime health, token activity, review backlog, and persisted alerts are grouped into a compact AI runtime console.
        </p>
      </div>
      <button class="primary-action" type="button" :disabled="isLoading" @click="loadOperations">
        <RefreshCw :size="18" aria-hidden="true" />
        {{ isLoading ? 'Refreshing data' : 'Refresh data' }}
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
            <p class="eyebrow">Runtime dependencies</p>
            <h3>Health matrix</h3>
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
        <p v-else class="answer-text">Loading runtime dependency health from /api/health.</p>
      </article>

      <article class="panel">
        <div class="panel-heading">
          <div>
            <p class="eyebrow">Recent Tasks</p>
            <h3>Learning and agent activity</h3>
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
            <p class="eyebrow">Recent Alerts</p>
            <h3>Persisted alert context</h3>
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
              {{ alert.status === 'ACKNOWLEDGED' ? 'Acknowledged' : 'Acknowledge' }}
            </button>
          </div>
        </div>
        <p v-else class="answer-text">No persisted alerts. New alerts will appear from /api/analytics/ops/alerts/persisted.</p>
      </article>

      <article class="panel">
        <div class="panel-heading">
          <div>
            <p class="eyebrow">Model Calls</p>
            <h3>Provider and review signals</h3>
          </div>
          <WalletCards :size="20" aria-hidden="true" />
        </div>
        <p v-if="!analytics" class="answer-text">Loading analytics overview from /api/analytics/overview.</p>
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
            <p class="eyebrow">Trace / Logs</p>
            <h3>Recent runtime context</h3>
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
            <p class="eyebrow">API sources</p>
            <h3>Current page contracts</h3>
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
            <p class="eyebrow">Available signals</p>
            <h3>Runtime health coverage</h3>
          </div>
          <Activity :size="20" aria-hidden="true" />
        </div>
        <div class="state-token-grid">
          <span class="state-token approved">Dependency health</span>
          <span class="state-token check">Token activity</span>
          <span class="state-token pending">Review backlog</span>
          <span class="state-token empty">Learning activity</span>
          <span class="state-token approved">Analytics overview count</span>
        </div>
      </article>
    </section>
  </section>
</template>
