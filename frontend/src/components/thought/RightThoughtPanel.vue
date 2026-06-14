<script setup lang="ts">
import { computed, ref } from 'vue'
import { Bot, ChevronLeft, ChevronRight, EyeOff, PanelRightOpen, X } from 'lucide-vue-next'
import AgentTimeline from './AgentTimeline.vue'
import CurrentTaskCard from './CurrentTaskCard.vue'
import RagSourcesCard from './RagSourcesCard.vue'
import RuntimeMetricsCard from './RuntimeMetricsCard.vue'
import {
  createDefaultThoughtPanelData,
  type CurrentThoughtTask,
  type ThoughtAgentStep,
  type ThoughtRagSources,
  type ThoughtRuntimeMetrics,
} from '../../types/thought'

const props = withDefaults(
  defineProps<{
    currentTask?: CurrentThoughtTask
    agentSteps?: ThoughtAgentStep[]
    ragSources?: ThoughtRagSources
    metrics?: ThoughtRuntimeMetrics
    initiallyCollapsed?: boolean
  }>(),
  {
    initiallyCollapsed: false,
  },
)

const emit = defineEmits<{
  (event: 'collapsed-change', value: boolean): void
  (event: 'hidden-change', value: boolean): void
}>()

const fallback = createDefaultThoughtPanelData()
const collapsed = ref(props.initiallyCollapsed)
const hidden = ref(false)

const panelData = computed(() => ({
  currentTask: props.currentTask ?? fallback.currentTask,
  agentSteps: props.agentSteps?.length ? props.agentSteps : fallback.agentSteps,
  ragSources: props.ragSources ?? fallback.ragSources,
  metrics: props.metrics ?? fallback.metrics,
}))

function toggleCollapsed() {
  const nextCollapsed = !collapsed.value
  collapsed.value = nextCollapsed
  emit('collapsed-change', nextCollapsed)
}

function hidePanel() {
  hidden.value = true
  emit('hidden-change', true)
}

function showPanel() {
  hidden.value = false
  collapsed.value = false
  emit('hidden-change', false)
  emit('collapsed-change', false)
}
</script>

<template>
  <aside
    v-if="!hidden"
    :class="['right-thought-panel', { collapsed }]"
    aria-label="AI 思考流"
    data-test="right-thought-panel"
  >
    <header :class="['thought-panel-header', { collapsed }]">
      <template v-if="!collapsed">
        <div class="title-lockup">
          <span class="title-mark">
            <Bot :size="18" aria-hidden="true" />
          </span>
          <div>
            <p class="thought-eyebrow">Thought Stream</p>
            <h2>AI 思考流</h2>
          </div>
        </div>

        <div class="panel-actions">
          <button
            type="button"
            data-test="thought-panel-collapse"
            aria-label="折叠 AI 思考流"
            title="折叠"
            @click="toggleCollapsed"
          >
            <ChevronLeft :size="16" aria-hidden="true" />
          </button>
          <button
            type="button"
            data-test="thought-panel-hide"
            aria-label="隐藏 AI 思考流"
            title="隐藏"
            @click="hidePanel"
          >
            <X :size="16" aria-hidden="true" />
          </button>
        </div>
      </template>

      <template v-else>
        <button
          class="collapsed-expand-button"
          type="button"
          data-test="thought-panel-expand"
          aria-label="展开 AI 思考流"
          title="展开"
          @click="toggleCollapsed"
        >
          <ChevronRight :size="16" aria-hidden="true" />
        </button>
      </template>
    </header>

    <div v-if="!collapsed" class="thought-panel-scroll">
      <CurrentTaskCard :task="panelData.currentTask" />
      <AgentTimeline :steps="panelData.agentSteps" />
      <RagSourcesCard :sources="panelData.ragSources" />
      <RuntimeMetricsCard :metrics="panelData.metrics" />
    </div>

    <div v-else class="collapsed-content" aria-label="AI 思考流已折叠">
      <Bot :size="20" aria-hidden="true" />
      <span>AI 思考流</span>
      <em :class="['collapsed-status', panelData.currentTask.status]">{{ panelData.currentTask.status }}</em>
    </div>
  </aside>

  <button
    v-else
    class="thought-panel-restore"
    type="button"
    data-test="thought-panel-restore"
    aria-label="显示 AI 思考流"
    title="显示 AI 思考流"
    @click="showPanel"
  >
    <PanelRightOpen :size="18" aria-hidden="true" />
    <span>AI 思考流</span>
    <EyeOff :size="14" aria-hidden="true" />
  </button>
</template>

<style scoped>
:global(.chat-shell) {
  display: grid;
  grid-template-columns: 292px minmax(0, 1fr) minmax(320px, 380px);
  min-height: 100svh;
}

:global(.workbench-main) {
  min-width: 0;
  overflow: auto;
}

.right-thought-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  width: 100%;
  min-width: 0;
  height: 100svh;
  color: #111827;
  background: #f8fafc;
  border-left: 1px solid #e5e7eb;
}

.right-thought-panel.collapsed {
  width: 88px;
  min-width: 88px;
  overflow: hidden;
}

.thought-panel-header {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: rgba(248, 250, 252, 0.96);
  border-bottom: 1px solid #e5e7eb;
  backdrop-filter: blur(14px);
}

.thought-panel-header.collapsed {
  justify-content: center;
  padding: 14px 10px 8px;
  border-bottom: 0;
}

.title-lockup {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.title-mark {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  color: #ffffff;
  background: linear-gradient(135deg, #4f46e5, #8b5cf6);
  border-radius: 10px;
}

.thought-eyebrow {
  color: #6b7280;
  font-size: 12px;
  font-weight: 700;
}

h2 {
  margin: 2px 0 0;
  color: #111827;
  font-size: 18px;
  line-height: 1.2;
  letter-spacing: 0;
}

.panel-actions {
  display: flex;
  gap: 6px;
  align-items: center;
}

.panel-actions button,
.collapsed-expand-button,
.thought-panel-restore {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 32px;
  color: #4b5563;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
}

.panel-actions button:hover,
.collapsed-expand-button:hover,
.thought-panel-restore:hover {
  color: #4f46e5;
  border-color: #c7d2fe;
}

.thought-panel-scroll {
  display: grid;
  align-content: start;
  gap: 12px;
  min-height: 0;
  padding: 14px;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.collapsed-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 10px;
  color: #4f46e5;
}

.collapsed-content span {
  color: #111827;
  font-size: 13px;
  font-weight: 800;
  line-height: 1.2;
  text-orientation: mixed;
  writing-mode: vertical-rl;
}

.collapsed-status {
  padding: 8px 4px;
  color: #5b21b6;
  font-size: 11px;
  font-style: normal;
  font-weight: 800;
  text-transform: uppercase;
  background: #ede9fe;
  border-radius: 999px;
  text-orientation: mixed;
  writing-mode: vertical-rl;
}

.collapsed-status.done {
  color: #047857;
  background: #d1fae5;
}

.collapsed-status.warning {
  color: #92400e;
  background: #fef3c7;
}

.collapsed-status.failed {
  color: #b91c1c;
  background: #fee2e2;
}

.thought-panel-restore {
  position: fixed;
  right: 16px;
  bottom: 16px;
  z-index: 30;
  gap: 8px;
  width: auto;
  padding: 0 10px;
  color: #ffffff;
  background: linear-gradient(135deg, #4f46e5, #8b5cf6);
  border-color: #c4b5fd;
  box-shadow: 0 14px 34px rgba(79, 70, 229, 0.22);
}

@media (max-width: 1220px) {
  :global(.chat-shell) {
    grid-template-columns: minmax(0, 1fr);
  }

  .right-thought-panel {
    height: auto;
    max-height: 720px;
    border-top: 1px solid #e5e7eb;
    border-left: 0;
  }

  .right-thought-panel.collapsed {
    width: 100%;
    min-width: 0;
  }

  .collapsed-content {
    align-items: start;
  }

  .collapsed-content span,
  .collapsed-status {
    writing-mode: horizontal-tb;
  }
}
</style>
