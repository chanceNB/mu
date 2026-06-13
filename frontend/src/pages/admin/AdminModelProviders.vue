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
  budget: 'Backend controlled',
  priority: 'Normal',
})

const providerTemplates = [
  {
    code: 'openai',
    name: 'OpenAI',
    detail: 'OpenAI-compatible production endpoint',
    status: 'Preset',
  },
  {
    code: 'claude',
    name: 'Claude',
    detail: 'Use Custom until backend exposes a first-class Claude preset',
    status: 'Custom',
  },
  {
    code: 'gemini',
    name: 'Gemini',
    detail: 'Use Custom until backend exposes a first-class Gemini preset',
    status: 'Custom',
  },
  {
    code: 'custom',
    name: 'Custom',
    detail: 'Bring any OpenAI-compatible base URL',
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
    label: 'Primary provider',
    value: primaryProvider.value?.displayName ?? 'Not selected',
    status: primaryProvider.value?.enabled ? 'ACTIVE' : 'MISSING',
  },
  {
    label: 'Backup provider',
    value: backupProvider.value?.displayName ?? 'No backup configured',
    status: backupProvider.value ? 'READY' : 'WAITING',
  },
  {
    label: 'Fallback strategy',
    value: backupProvider.value ? 'Use enabled backup when primary fails' : 'Manual recovery until backup exists',
    status: backupProvider.value ? 'READY' : 'WATCH',
  },
  {
    label: 'Last test result',
    value: successMessage.value || errorMessage.value || 'No test result in this session',
    status: errorMessage.value ? 'FAILED' : successMessage.value ? 'SUCCEEDED' : 'IDLE',
  },
  {
    label: 'Token usage',
    value: 'Tracked in Operations analytics',
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
    errorMessage.value = error instanceof Error ? error.message : 'Unable to load model providers'
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
  form.budget = 'Backend controlled'
  form.priority = templateCode === 'openai' ? 'High' : 'Normal'

  if (templateCode in PROVIDER_PRESETS) {
    applyPreset(templateCode)
    return
  }

  form.providerCode = 'custom'
  form.displayName = providerTemplates.find((template) => template.code === templateCode)?.name ?? 'Custom'
  form.remark = templateCode === 'custom' ? '' : `${form.displayName} custom endpoint`
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
  form.budget = 'Backend controlled'
  form.priority = provider.defaultProvider ? 'High' : provider.enabled ? 'Normal' : 'Paused'
}

function resetForm() {
  selectedProviderId.value = ''
  form.apiKey = ''
  form.defaultProvider = false
  form.enabled = true
  form.budget = 'Backend controlled'
  form.priority = 'Normal'
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
    successMessage.value = isEditing.value ? 'Provider configuration updated.' : 'Provider created.'
    await loadProviders()
    selectProvider(saved)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Unable to save model provider'
  } finally {
    isSaving.value = false
  }
}

async function markDefault(provider: ModelProviderSummary) {
  errorMessage.value = ''
  try {
    const updated = await setDefaultModelProvider(provider.id)
    successMessage.value = `${updated.displayName} is now the default provider.`
    await loadProviders()
    selectProvider(updated)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Unable to set default provider'
  }
}

async function testConnection(provider: ModelProviderSummary) {
  testingProviderId.value = provider.id
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const result = await testModelProviderConnection(provider.id)
    if (result.status === 'SUCCEEDED') {
      successMessage.value = `${provider.displayName} test call succeeded in ${result.latencyMs} ms.`
    } else {
      errorMessage.value = `${provider.displayName} test call failed: ${result.errorCode ?? 'UNKNOWN'}`
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Unable to test provider connection'
  } finally {
    testingProviderId.value = ''
  }
}
</script>

<template>
  <section class="workspace secondary-workspace" aria-label="Admin model providers" data-test="admin-model-providers">
    <header class="workspace-header secondary-page-header">
      <div>
        <p class="eyebrow">Model Provider Registry</p>
        <h2>Model Providers</h2>
        <p class="header-note">
          Configure enabled providers, masked API keys, base URLs, models, priorities, and test calls while keeping secrets in the backend.
        </p>
      </div>
      <button class="primary-action" type="button" :disabled="isLoading" @click="loadProviders">
        <RefreshCw :size="18" aria-hidden="true" />
        {{ isLoading ? 'Refreshing providers' : 'Refresh providers' }}
      </button>
    </header>

    <section class="provider-layout">
      <aside class="provider-list-column" aria-label="Provider list">
        <article class="panel provider-template-panel">
          <div class="panel-heading">
            <div>
              <p class="eyebrow">Provider catalog</p>
              <h3>Start from a provider</h3>
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
            New DeepSeek provider
          </button>
        </article>

        <article class="panel triage-panel">
          <div class="panel-heading">
            <div>
              <p class="eyebrow">Registered</p>
              <h3>Saved providers</h3>
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
          <p v-else class="answer-text">No providers yet. Create the first provider from a preset or custom endpoint.</p>
        </article>
      </aside>

      <div class="provider-config-column">
        <article class="panel triage-panel provider-form-panel" data-test="provider-form">
          <div class="panel-heading">
            <div>
              <p class="eyebrow">{{ isEditing ? 'Edit provider' : 'New provider' }}</p>
              <h3>{{ form.displayName || 'Provider configuration' }}</h3>
            </div>
            <Activity :size="20" aria-hidden="true" />
          </div>

          <p v-if="errorMessage" class="error-text" role="status">{{ errorMessage }}</p>
          <p v-if="successMessage" class="answer-text success-text" role="status">{{ successMessage }}</p>

          <div class="form-grid provider-form-grid">
            <label>
              Provider type
              <select v-model="form.providerCode" :disabled="isEditing" @change="applyPreset(form.providerCode)">
                <option value="deepseek">DeepSeek</option>
                <option value="mimo">Xiaomi MiMo</option>
                <option value="dashscope">DashScope</option>
                <option value="openai">OpenAI</option>
                <option value="custom">Custom</option>
              </select>
            </label>
            <label>
              Display name
              <input v-model="form.displayName" type="text" placeholder="Provider display name" />
            </label>
            <label>
              Remark
              <input v-model="form.remark" type="text" placeholder="Optional operations note" />
            </label>
            <label>
              Website
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
              API key masked
              <input
                v-model="form.apiKey"
                type="password"
                :placeholder="selectedProvider?.apiKeyConfigured ? 'Leave blank to keep the current encrypted key' : 'Enter API key'"
              />
            </label>
            <label>
              Budget
              <input v-model="form.budget" type="text" placeholder="Backend controlled" />
            </label>
            <label>
              Priority
              <select v-model="form.priority">
                <option>High</option>
                <option>Normal</option>
                <option>Low</option>
                <option>Paused</option>
              </select>
            </label>
            <label class="checkbox-row">
              <input v-model="form.enabled" type="checkbox" />
              Enabled
            </label>
            <label class="checkbox-row">
              <input v-model="form.defaultProvider" type="checkbox" />
              Default provider
            </label>
          </div>

          <div class="action-row">
            <button class="primary-action" type="button" :disabled="isSaving" data-test="save-provider" @click="saveProvider">
              <CheckCircle2 :size="16" aria-hidden="true" />
              {{ isSaving ? 'Saving' : isEditing ? 'Update provider' : 'Create provider' }}
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
              {{ testingProviderId === selectedProvider.id ? 'Testing call' : 'Test call' }}
            </button>
            <button
              v-if="selectedProvider && !selectedProvider.defaultProvider"
              class="secondary-action"
              type="button"
              data-test="set-default-provider"
              @click="markDefault(selectedProvider)"
            >
              <Star :size="16" aria-hidden="true" />
              Set default
            </button>
          </div>
        </article>

        <article class="panel">
          <div class="panel-heading">
            <div>
              <p class="eyebrow">Call chain / monitoring</p>
              <h3>Provider runtime policy</h3>
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
