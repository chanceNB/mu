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
  WalletCards,
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
    code: 'openai',
    name: 'OpenAI',
    detail: 'OpenAI-compatible 生产端点',
    status: 'Preset',
  },
  {
    code: 'claude',
    name: 'Claude',
    detail: '在后端提供一等 Claude 预设前，请使用 Custom',
    status: 'Custom',
  },
  {
    code: 'gemini',
    name: 'Gemini',
    detail: '在后端提供一等 Gemini 预设前，请使用 Custom',
    status: 'Custom',
  },
  {
    code: 'custom',
    name: 'Custom',
    detail: '接入任意 OpenAI-compatible Base URL',
    status: 'Manual',
  },
]

const isEditing = computed(() => selectedProviderId.value !== '')
const selectedProvider = computed(
  () => providers.value.find((provider) => provider.id === selectedProviderId.value) ?? null,
)

const primaryProvider = computed(
  () => providers.value.find((provider) => provider.defaultProvider) ?? providers.value.find((provider) => provider.enabled) ?? null,
)

const backupProvider = computed(
  () => providers.value.find((provider) => provider.enabled && provider.id !== primaryProvider.value?.id) ?? null,
)

const providerMonitorItems = computed(() => [
  {
    label: '主 Provider',
    value: primaryProvider.value?.displayName ?? '未选择',
    status: primaryProvider.value?.enabled ? 'ACTIVE' : 'MISSING',
  },
  {
    label: '备用 Provider',
    value: backupProvider.value?.displayName ?? '未配置备用 Provider',
    status: backupProvider.value ? 'READY' : 'WAITING',
  },
  {
    label: 'Fallback 策略',
    value: backupProvider.value ? '主 Provider 失败时使用已启用备用 Provider' : '配置备用 Provider 前需要手动恢复',
    status: backupProvider.value ? 'READY' : 'WATCH',
  },
  {
    label: '最近测试结果',
    value: successMessage.value || errorMessage.value || '当前会话暂无测试结果',
    status: errorMessage.value ? 'FAILED' : successMessage.value ? 'SUCCEEDED' : 'IDLE',
  },
  {
    label: 'Token 用量',
    value: '在运维 analytics 中跟踪',
    status: 'OBSERVED',
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
      errorMessage.value = `${provider.displayName} 测试调用失败：${result.errorCode ?? 'UNKNOWN'}`
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '无法测试 Provider 连接'
  } finally {
    testingProviderId.value = ''
  }
}
</script>

<template>
  <section class="workspace secondary-workspace" aria-label="管理员 Model Providers 配置" data-test="admin-model-providers">
    <header class="workspace-header secondary-page-header">
      <div>
        <p class="eyebrow">Model Provider 注册表</p>
        <h2>Model Providers 配置</h2>
        <p class="header-note">
          配置已启用的 Provider、脱敏 API key、Base URL、Model、优先级和测试调用，同时将密钥保留在后端。
        </p>
      </div>
      <button class="primary-action" type="button" :disabled="isLoading" @click="loadProviders">
        <RefreshCw :size="18" aria-hidden="true" />
        {{ isLoading ? '正在刷新 Provider' : '刷新 Provider' }}
      </button>
    </header>

    <section class="provider-layout">
      <aside class="provider-list-column" aria-label="Provider 列表">
        <article class="panel provider-template-panel">
          <div class="panel-heading">
            <div>
              <p class="eyebrow">Provider 目录</p>
              <h3>从 Provider 开始</h3>
            </div>
            <ServerCog :size="20" aria-hidden="true" />
          </div>
          <div class="provider-template-grid">
            <button
              v-for="template in providerTemplates"
              :key="template.code"
              type="button"
              class="provider-template-card"
              @click="startProviderDraft(template.code)"
            >
              <strong>{{ template.name }}</strong>
              <span>{{ template.detail }}</span>
              <em>{{ template.status }}</em>
            </button>
          </div>
          <button class="tool-button secondary" type="button" data-test="new-provider" @click="resetForm">
            <PlugZap :size="16" aria-hidden="true" />
            新建 DeepSeek Provider
          </button>
        </article>

        <article class="panel triage-panel">
          <div class="panel-heading">
            <div>
              <p class="eyebrow">已注册</p>
              <h3>已保存 Provider</h3>
            </div>
            <ShieldCheck :size="20" aria-hidden="true" />
          </div>
          <ul v-if="providers.length" class="document-list provider-list">
            <li v-for="provider in providers" :key="provider.id" :class="{ selected: provider.id === selectedProviderId }">
              <PlugZap :size="16" aria-hidden="true" />
              <button
                type="button"
                class="link-button"
                :data-test="`select-provider-${provider.providerCode}`"
                @click="selectProvider(provider)"
              >
                <strong>{{ provider.displayName }}</strong>
                <span>{{ provider.providerCode }} / {{ provider.baseUrl }}</span>
              </button>
              <em :class="['status-pill', provider.enabled ? 'approved' : 'failed']">
                {{ provider.defaultProvider ? 'DEFAULT' : provider.enabled ? 'ENABLED' : 'DISABLED' }}
              </em>
            </li>
          </ul>
          <p v-else class="answer-text">暂无 Provider。请从预设或 Custom endpoint 创建第一个 Provider。</p>
        </article>
      </aside>

      <div class="provider-config-column">
        <article class="panel triage-panel provider-form-panel" data-test="provider-form">
          <div class="panel-heading">
            <div>
              <p class="eyebrow">{{ isEditing ? '编辑 Provider' : '新建 Provider' }}</p>
              <h3>{{ form.displayName || 'Provider 配置' }}</h3>
            </div>
            <Activity :size="20" aria-hidden="true" />
          </div>

          <p v-if="errorMessage" class="error-text" role="status">{{ errorMessage }}</p>
          <p v-if="successMessage" class="answer-text success-text" role="status">{{ successMessage }}</p>

          <div class="form-grid provider-form-grid">
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
            <label>
              备注
              <input v-model="form.remark" type="text" placeholder="可选运维备注" />
            </label>
            <label>
              官网
              <input v-model="form.websiteUrl" type="url" placeholder="https://..." />
            </label>
            <label class="wide-field">
              Base URL
              <input v-model="form.baseUrl" type="url" placeholder="https://api.example.com/v1" />
            </label>
            <label>
              Chat model
              <input v-model="form.chatModel" type="text" placeholder="chat model id" />
            </label>
            <label>
              Embedding model
              <input v-model="form.embeddingModel" type="text" placeholder="embedding model id" />
            </label>
            <label>
              API key 已脱敏
              <input
                v-model="form.apiKey"
                type="password"
                :placeholder="selectedProvider?.apiKeyConfigured ? '留空则保留当前加密 key' : '输入 API key'"
              />
            </label>
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

          <div class="action-row">
            <button class="primary-action" type="button" :disabled="isSaving" data-test="save-provider" @click="saveProvider">
              <CheckCircle2 :size="16" aria-hidden="true" />
              {{ isSaving ? '正在保存' : isEditing ? '更新 Provider' : '创建 Provider' }}
            </button>
            <button
              v-if="selectedProvider"
              class="secondary-action"
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
              class="secondary-action"
              type="button"
              data-test="set-default-provider"
              @click="markDefault(selectedProvider)"
            >
              <Star :size="16" aria-hidden="true" />
              设为默认
            </button>
          </div>
        </article>

        <article class="panel">
          <div class="panel-heading">
            <div>
              <p class="eyebrow">调用链 / 监控</p>
              <h3>Provider Runtime 策略</h3>
            </div>
            <GitBranch :size="20" aria-hidden="true" />
          </div>
          <ul class="document-list compact-evidence-list">
            <li v-for="item in providerMonitorItems" :key="item.label">
              <WalletCards :size="16" aria-hidden="true" />
              <div>
                <strong>{{ item.label }}</strong>
                <span>{{ item.value }}</span>
              </div>
              <em :class="['status-pill', item.status.toLowerCase()]">{{ item.status }}</em>
            </li>
          </ul>
        </article>
      </div>
    </section>
  </section>
</template>
