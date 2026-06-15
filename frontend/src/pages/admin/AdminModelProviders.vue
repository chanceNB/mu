<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  Activity,
  CheckCircle2,
  GitBranch,
  PlugZap,
  RefreshCw,
  ServerCog,
  ShieldCheck,
  Star,
} from 'lucide-vue-next'
import {
  PROVIDER_PRESETS,
  createModelProvider,
  listModelProviders,
  setDefaultModelProvider,
  testModelProviderConnection,
  updateModelProvider,
  type ModelProviderSummary,
} from '../../api/modelProviders'
import MobbinGlassCard from '../../components/mobbin/MobbinGlassCard.vue'
import MobbinHero from '../../components/mobbin/MobbinHero.vue'
import MobbinMetricStrip, { type MobbinMetricItem } from '../../components/mobbin/MobbinMetricStrip.vue'
import MobbinPageShell from '../../components/mobbin/MobbinPageShell.vue'
import MobbinPreviewFrame from '../../components/mobbin/MobbinPreviewFrame.vue'
import MobbinStatusPill from '../../components/mobbin/MobbinStatusPill.vue'
import MobbinTimeline, { type MobbinTimelineItem } from '../../components/mobbin/MobbinTimeline.vue'

const providers = ref<ModelProviderSummary[]>([])
const isLoading = ref(false)
const isSaving = ref(false)
const testingProviderId = ref('')
const errorMessage = ref('')
const successMessage = ref('')
const selectedProviderId = ref('')

const form = reactive({
  providerCode: 'deepseek',
  displayName: PROVIDER_PRESETS.deepseek.displayName,
  remark: '',
  websiteUrl: PROVIDER_PRESETS.deepseek.websiteUrl,
  baseUrl: PROVIDER_PRESETS.deepseek.baseUrl,
  chatModel: PROVIDER_PRESETS.deepseek.chatModel,
  embeddingModel: PROVIDER_PRESETS.deepseek.embeddingModel,
  apiKey: '',
  enabled: true,
  defaultProvider: false,
  budget: '后端控制',
  priority: '普通',
})

const providerTemplates = [
  {
    code: 'deepseek',
    name: 'DeepSeek',
    detail: '适合中文学习场景的 OpenAI-compatible 模型服务。',
    status: 'Preset',
  },
  {
    code: 'openai',
    name: 'OpenAI',
    detail: 'OpenAI 官方生产端点，支持 Chat 与 Embedding 模型。',
    status: 'Preset',
  },
  {
    code: 'dashscope',
    name: 'DashScope',
    detail: '阿里云 DashScope compatible-mode endpoint。',
    status: 'Preset',
  },
  {
    code: 'mimo',
    name: 'Xiaomi MiMo',
    detail: '小米 MiMo 模型服务预设。',
    status: 'Preset',
  },
  {
    code: 'custom',
    name: 'Custom',
    detail: '接入任意 OpenAI-compatible Base URL。',
    status: 'Manual',
  },
]

const isEditing = computed(() => selectedProviderId.value !== '')
const selectedProvider = computed(() => providers.value.find((provider) => provider.id === selectedProviderId.value) ?? null)
const primaryProvider = computed(
  () => providers.value.find((provider) => provider.defaultProvider) ?? providers.value.find((provider) => provider.enabled) ?? null,
)
const backupProvider = computed(
  () => providers.value.find((provider) => provider.enabled && provider.id !== primaryProvider.value?.id) ?? null,
)
const enabledCount = computed(() => providers.value.filter((provider) => provider.enabled).length)

const metrics = computed<MobbinMetricItem[]>(() => [
  { label: '默认 Provider', value: primaryProvider.value?.displayName ?? '未配置', note: primaryProvider.value?.providerCode ?? '需要设置默认通道' },
  { label: '启用数量', value: enabledCount.value, note: `${providers.value.length} 个已注册 Provider` },
  { label: '最近测试结果', value: successMessage.value ? '成功' : errorMessage.value ? '失败' : '暂无', note: successMessage.value || errorMessage.value || '当前会话尚未测试' },
  { label: 'Token 策略', value: form.budget, note: 'Token 消耗仍由后端治理策略控制' },
])

const providerMonitorItems = computed<MobbinTimelineItem[]>(() => [
  {
    title: '默认 Provider',
    subtitle: primaryProvider.value?.displayName ?? '未选择',
    detail: primaryProvider.value?.baseUrl ?? '保存并设为默认后生效。',
    status: primaryProvider.value?.enabled ? 'ACTIVE' : 'MISSING',
  },
  {
    title: '备用 Provider',
    subtitle: backupProvider.value?.displayName ?? '未配置备用 Provider',
    detail: backupProvider.value ? backupProvider.value.baseUrl : '配置备用通道后可支撑 Fallback。',
    status: backupProvider.value ? 'READY' : 'WAITING',
  },
  {
    title: 'Fallback 策略',
    subtitle: backupProvider.value ? '已具备备用通道' : '需要手动配置',
    detail: '失败切换策略仍由后端运行时执行，前端只展示配置状态。',
    status: backupProvider.value ? 'READY' : 'WATCH',
  },
  {
    title: '最近测试结果',
    subtitle: successMessage.value || errorMessage.value || '暂无测试结果',
    detail: '测试连接不会回显 API key 明文。',
    status: errorMessage.value ? 'FAILED' : successMessage.value ? 'SUCCEEDED' : 'IDLE',
  },
])

onMounted(() => {
  void loadProviders()
})

async function loadProviders() {
  isLoading.value = true
  errorMessage.value = ''
  try {
    providers.value = await listModelProviders()
    if (!selectedProviderId.value && providers.value.length > 0) {
      selectProvider(providers.value[0])
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '无法加载 Model Providers'
  } finally {
    isLoading.value = false
  }
}

function applyPreset(providerCode: string) {
  const preset = PROVIDER_PRESETS[providerCode]
  if (!preset) return
  form.providerCode = providerCode
  form.displayName = preset.displayName
  form.websiteUrl = preset.websiteUrl
  form.baseUrl = preset.baseUrl
  form.chatModel = preset.chatModel
  form.embeddingModel = preset.embeddingModel
}

function startProviderDraft(templateCode: string) {
  selectedProviderId.value = ''
  form.apiKey = ''
  form.defaultProvider = false
  form.enabled = true
  form.budget = '后端控制'
  form.priority = templateCode === 'openai' ? '高' : '普通'

  if (templateCode in PROVIDER_PRESETS) {
    applyPreset(templateCode)
    return
  }

  form.providerCode = 'custom'
  form.displayName = providerTemplates.find((template) => template.code === templateCode)?.name ?? 'Custom'
  form.remark = templateCode === 'custom' ? '' : `${form.displayName} Custom endpoint`
  form.websiteUrl = ''
  form.baseUrl = ''
  form.chatModel = ''
  form.embeddingModel = ''
}

function selectProvider(provider: ModelProviderSummary) {
  selectedProviderId.value = provider.id
  form.providerCode = provider.providerCode
  form.displayName = provider.displayName
  form.remark = provider.remark ?? ''
  form.websiteUrl = provider.websiteUrl ?? ''
  form.baseUrl = provider.baseUrl
  form.chatModel = provider.chatModel ?? ''
  form.embeddingModel = provider.embeddingModel ?? ''
  form.apiKey = provider.apiKeyMasked ?? ''
  form.enabled = provider.enabled
  form.defaultProvider = provider.defaultProvider
  form.budget = '后端控制'
  form.priority = provider.defaultProvider ? '高' : provider.enabled ? '普通' : '暂停'
}

function resetForm() {
  selectedProviderId.value = ''
  form.apiKey = ''
  form.defaultProvider = false
  form.enabled = true
  form.budget = '后端控制'
  form.priority = '普通'
  applyPreset('deepseek')
}

async function saveProvider() {
  isSaving.value = true
  errorMessage.value = ''
  successMessage.value = ''
  const payload = {
    providerCode: form.providerCode,
    displayName: form.displayName.trim(),
    remark: form.remark.trim() || undefined,
    websiteUrl: form.websiteUrl.trim() || undefined,
    baseUrl: form.baseUrl.trim(),
    chatModel: form.chatModel.trim() || undefined,
    embeddingModel: form.embeddingModel.trim() || undefined,
    apiKey: form.apiKey.trim() || undefined,
    enabled: form.enabled,
    defaultProvider: form.defaultProvider,
  }
  try {
    const saved = isEditing.value
      ? await updateModelProvider(selectedProviderId.value, payload)
      : await createModelProvider(payload)
    successMessage.value = isEditing.value ? 'Provider 配置已更新。' : 'Provider 已创建。'
    await loadProviders()
    selectProvider(saved)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '无法保存 Model Provider'
  } finally {
    isSaving.value = false
  }
}

async function markDefault(provider: ModelProviderSummary) {
  errorMessage.value = ''
  try {
    const updated = await setDefaultModelProvider(provider.id)
    successMessage.value = `${updated.displayName} 已设为默认 Provider。`
    await loadProviders()
    selectProvider(updated)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '无法设置默认 Provider'
  }
}

async function testConnection(provider: ModelProviderSummary) {
  testingProviderId.value = provider.id
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const result = await testModelProviderConnection(provider.id)
    if (result.status === 'SUCCEEDED') {
      successMessage.value = `${provider.displayName} 测试调用成功，用时 ${result.latencyMs} ms。`
    } else {
      errorMessage.value = `${provider.displayName} 测试调用失败，${result.errorCode ?? 'UNKNOWN'}`
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '无法测试 Provider 连接'
  } finally {
    testingProviderId.value = ''
  }
}
</script>

<template>
  <MobbinPageShell aria-label="模型供应商中心" data-test="admin-model-providers">
    <MobbinHero
      eyebrow="Model Provider Hub"
      title="模型供应商中心"
      description="以 marketplace 的方式管理 DeepSeek、OpenAI、DashScope、Xiaomi MiMo 与 Custom Provider，统一查看默认通道、连接状态、模型配置和治理策略。"
    >
      <template #actions>
        <button class="mobbin-primary-button" type="button" :disabled="isLoading" @click="loadProviders">
          <RefreshCw :size="18" aria-hidden="true" />
          {{ isLoading ? '正在刷新 Provider' : '刷新 Provider' }}
        </button>
      </template>
      <template #preview>
        <MobbinPreviewFrame label="Provider Strategy">
          <div class="provider-strategy-preview">
            <PlugZap :size="24" aria-hidden="true" />
            <strong>{{ primaryProvider?.displayName ?? '未配置默认 Provider' }}</strong>
            <p>{{ primaryProvider?.baseUrl ?? '选择一个 Provider 并保存为默认后，模型调用会使用该通道。' }}</p>
            <div>
              <MobbinStatusPill :status="primaryProvider?.enabled ? 'ACTIVE' : 'MISSING'">
                {{ primaryProvider?.enabled ? 'Active' : 'Missing' }}
              </MobbinStatusPill>
              <MobbinStatusPill :status="backupProvider ? 'READY' : 'WAITING'">
                {{ backupProvider ? 'Fallback Ready' : 'No Fallback' }}
              </MobbinStatusPill>
            </div>
          </div>
        </MobbinPreviewFrame>
      </template>
    </MobbinHero>

    <MobbinMetricStrip :items="metrics" />

    <p v-if="errorMessage" class="mobbin-error" role="status">{{ errorMessage }}</p>
    <p v-if="successMessage" class="mobbin-success" role="status">{{ successMessage }}</p>

    <section class="provider-hub-layout">
      <div class="provider-market-column">
        <MobbinGlassCard eyebrow="Provider Marketplace" title="从模板开始" elevated>
          <template #icon>
            <ServerCog :size="20" aria-hidden="true" />
          </template>
          <div class="marketplace-grid">
            <button
              v-for="template in providerTemplates"
              :key="template.code"
              type="button"
              class="marketplace-card"
              @click="startProviderDraft(template.code)"
            >
              <span class="provider-logo">{{ template.name.slice(0, 2).toUpperCase() }}</span>
              <strong>{{ template.name }}</strong>
              <p>{{ template.detail }}</p>
              <MobbinStatusPill :status="template.status">{{ template.status }}</MobbinStatusPill>
            </button>
          </div>
          <button class="mobbin-secondary-button" type="button" data-test="new-provider" @click="resetForm">
            <PlugZap :size="16" aria-hidden="true" />
            新建 DeepSeek Provider
          </button>
        </MobbinGlassCard>

        <MobbinGlassCard eyebrow="Connections" title="已注册 Provider">
          <template #icon>
            <ShieldCheck :size="20" aria-hidden="true" />
          </template>
          <div v-if="providers.length" class="connection-grid">
            <article v-for="provider in providers" :key="provider.id" :class="['connection-card', { selected: provider.id === selectedProviderId }]">
              <button
                type="button"
                class="connection-main"
                :data-test="`select-provider-${provider.providerCode}`"
                @click="selectProvider(provider)"
              >
                <span class="provider-logo small">{{ provider.displayName.slice(0, 2).toUpperCase() }}</span>
                <span>
                  <strong>{{ provider.displayName }}</strong>
                  <small>{{ provider.providerCode }} / {{ provider.baseUrl }}</small>
                </span>
              </button>
              <div class="connection-meta">
                <MobbinStatusPill :status="provider.enabled ? 'ENABLED' : 'DISABLED'">
                  {{ provider.defaultProvider ? 'DEFAULT' : provider.enabled ? 'ENABLED' : 'DISABLED' }}
                </MobbinStatusPill>
                <span>Chat: {{ provider.chatModel ?? '未配置' }}</span>
                <span>Embedding: {{ provider.embeddingModel ?? '未配置' }}</span>
              </div>
              <div class="connection-actions">
                <button type="button" @click="selectProvider(provider)">编辑</button>
                <button type="button" :disabled="testingProviderId === provider.id" @click="testConnection(provider)">
                  {{ testingProviderId === provider.id ? '测试中' : '测试连接' }}
                </button>
                <button v-if="!provider.defaultProvider" type="button" @click="markDefault(provider)">设为默认</button>
              </div>
            </article>
          </div>
          <p v-else class="mobbin-empty">暂无 Provider。请从预设或 Custom endpoint 创建第一个 Provider。</p>
        </MobbinGlassCard>
      </div>

      <div class="provider-config-column">
        <MobbinGlassCard eyebrow="Configuration" :title="form.displayName || 'Provider 配置'" elevated data-test="provider-form">
          <template #icon>
            <Activity :size="20" aria-hidden="true" />
          </template>

          <div class="config-section-grid">
            <section class="config-section">
              <h4>基础信息</h4>
              <div class="form-grid two-col">
                <label>
                  Provider 类型
                  <select v-model="form.providerCode" :disabled="isEditing" @change="applyPreset(form.providerCode)">
                    <option value="deepseek">DeepSeek</option>
                    <option value="mimo">Xiaomi MiMo</option>
                    <option value="dashscope">DashScope</option>
                    <option value="openai">OpenAI</option>
                    <option value="custom">Custom</option>
                  </select>
                </label>
                <label>
                  展示名称
                  <input v-model="form.displayName" type="text" placeholder="Provider 展示名称" />
                </label>
                <label class="wide-field">
                  备注
                  <input v-model="form.remark" type="text" placeholder="可选运维备注" />
                </label>
                <label class="wide-field">
                  官网
                  <input v-model="form.websiteUrl" type="url" placeholder="https://..." />
                </label>
              </div>
            </section>

            <section class="config-section">
              <h4>连接信息</h4>
              <div class="form-grid">
                <label>
                  Base URL
                  <input v-model="form.baseUrl" type="url" placeholder="https://api.example.com/v1" />
                </label>
                <label>
                  API key 已脱敏
                  <input
                    v-model="form.apiKey"
                    type="password"
                    :placeholder="selectedProvider?.apiKeyConfigured ? '留空则保留当前加密 key' : '输入 API key'"
                  />
                </label>
              </div>
            </section>

            <section class="config-section">
              <h4>模型配置</h4>
              <div class="form-grid two-col">
                <label>
                  Chat model
                  <input v-model="form.chatModel" type="text" placeholder="chat model id" />
                </label>
                <label>
                  Embedding model
                  <input v-model="form.embeddingModel" type="text" placeholder="embedding model id" />
                </label>
              </div>
            </section>

            <section class="config-section">
              <h4>治理策略</h4>
              <div class="form-grid two-col">
                <label>
                  预算
                  <input v-model="form.budget" type="text" placeholder="后端控制" />
                </label>
                <label>
                  优先级
                  <select v-model="form.priority">
                    <option>高</option>
                    <option>普通</option>
                    <option>低</option>
                    <option>暂停</option>
                  </select>
                </label>
                <label class="checkbox-row">
                  <input v-model="form.enabled" type="checkbox" />
                  已启用
                </label>
                <label class="checkbox-row">
                  <input v-model="form.defaultProvider" type="checkbox" />
                  默认 Provider
                </label>
              </div>
            </section>
          </div>

          <div class="action-row">
            <button class="mobbin-primary-button" type="button" :disabled="isSaving" data-test="save-provider" @click="saveProvider">
              <CheckCircle2 :size="16" aria-hidden="true" />
              {{ isSaving ? '正在保存' : isEditing ? '更新 Provider' : '创建 Provider' }}
            </button>
            <button
              v-if="selectedProvider"
              class="mobbin-secondary-button"
              type="button"
              :disabled="testingProviderId === selectedProvider.id"
              data-test="test-provider"
              @click="testConnection(selectedProvider)"
            >
              <Activity :size="16" aria-hidden="true" />
              {{ testingProviderId === selectedProvider.id ? '正在测试调用' : '测试调用' }}
            </button>
            <button
              v-if="selectedProvider && !selectedProvider.defaultProvider"
              class="mobbin-secondary-button"
              type="button"
              data-test="set-default-provider"
              @click="markDefault(selectedProvider)"
            >
              <Star :size="16" aria-hidden="true" />
              设为默认
            </button>
          </div>
        </MobbinGlassCard>

        <MobbinGlassCard eyebrow="Provider Strategy" title="运行策略与监控">
          <template #icon>
            <GitBranch :size="20" aria-hidden="true" />
          </template>
          <MobbinTimeline :items="providerMonitorItems" />
        </MobbinGlassCard>
      </div>
    </section>
  </MobbinPageShell>
</template>

<style scoped>
.mobbin-primary-button,
.mobbin-secondary-button {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  padding: 10px 14px;
  font: inherit;
  font-weight: 900;
  border-radius: 12px;
  cursor: pointer;
}

.mobbin-primary-button {
  color: #ffffff;
  background: linear-gradient(135deg, #4f46e5, #2563eb);
  border: 1px solid transparent;
  box-shadow: 0 14px 26px rgba(79, 70, 229, 0.22);
}

.mobbin-secondary-button {
  color: #4f46e5;
  background: #eef2ff;
  border: 1px solid #c7d2fe;
}

.provider-strategy-preview {
  display: grid;
  gap: 10px;
}

.provider-strategy-preview svg {
  color: #4f46e5;
}

.provider-strategy-preview strong {
  color: #0f172a;
  font-size: 22px;
  overflow-wrap: anywhere;
}

.provider-strategy-preview p,
.marketplace-card p,
.connection-main small,
.connection-meta span,
.mobbin-empty {
  color: #64748b;
  font-size: 13px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.provider-strategy-preview div {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.provider-hub-layout {
  display: grid;
  grid-template-columns: minmax(330px, 0.9fr) minmax(0, 1.35fr);
  gap: 16px;
  align-items: start;
}

.provider-market-column,
.provider-config-column {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.marketplace-grid,
.connection-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.marketplace-card {
  display: grid;
  gap: 9px;
  min-width: 0;
  min-height: 176px;
  padding: 15px;
  color: inherit;
  text-align: left;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  cursor: pointer;
  transition:
    transform 160ms ease,
    box-shadow 160ms ease,
    border-color 160ms ease;
}

.marketplace-card:hover,
.connection-card:hover {
  border-color: #c7d2fe;
  box-shadow: 0 18px 34px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

.provider-logo {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  color: #ffffff;
  font-size: 12px;
  font-weight: 900;
  background: linear-gradient(135deg, #0f172a, #4f46e5);
  border-radius: 14px;
}

.provider-logo.small {
  width: 38px;
  height: 38px;
  border-radius: 13px;
}

.marketplace-card strong,
.connection-main strong {
  color: #0f172a;
  font-size: 15px;
  overflow-wrap: anywhere;
}

.connection-card {
  display: grid;
  gap: 12px;
  min-width: 0;
  padding: 14px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  transition:
    transform 160ms ease,
    box-shadow 160ms ease,
    border-color 160ms ease;
}

.connection-card.selected {
  background:
    linear-gradient(#f8fbff, #f8fbff) padding-box,
    linear-gradient(135deg, #4f46e5, #14b8a6) border-box;
  border-color: transparent;
}

.connection-main {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 0;
  color: inherit;
  text-align: left;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.connection-main span:last-child {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.connection-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  align-items: center;
}

.connection-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.connection-actions button {
  min-height: 32px;
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

.config-section-grid {
  display: grid;
  gap: 14px;
}

.config-section {
  display: grid;
  gap: 12px;
  padding: 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
}

.config-section h4 {
  margin: 0;
  color: #0f172a;
  font-size: 15px;
  letter-spacing: 0;
}

.form-grid {
  display: grid;
  gap: 12px;
}

.form-grid.two-col {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.form-grid label {
  display: grid;
  gap: 6px;
  color: #64748b;
  font-size: 12px;
  font-weight: 900;
}

.form-grid input,
.form-grid select {
  width: 100%;
  min-height: 42px;
  padding: 10px 12px;
  color: #0f172a;
  font: inherit;
  background: #ffffff;
  border: 1px solid #dbe3ef;
  border-radius: 12px;
}

.wide-field {
  grid-column: 1 / -1;
}

.checkbox-row {
  display: flex !important;
  gap: 8px;
  align-items: center;
  min-height: 42px;
  padding: 10px 12px;
  color: #0f172a !important;
  background: #ffffff;
  border: 1px solid #dbe3ef;
  border-radius: 12px;
}

.checkbox-row input {
  width: auto;
  min-height: 0;
  padding: 0;
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.mobbin-empty,
.mobbin-error,
.mobbin-success {
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

.mobbin-success {
  color: #047857;
  background: #ecfdf5;
  border-color: #a7f3d0;
}

@media (max-width: 1180px) {
  .provider-hub-layout,
  .marketplace-grid,
  .connection-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .form-grid.two-col,
  .connection-main {
    grid-template-columns: 1fr;
  }

  .mobbin-primary-button,
  .mobbin-secondary-button {
    width: 100%;
  }
}
</style>
