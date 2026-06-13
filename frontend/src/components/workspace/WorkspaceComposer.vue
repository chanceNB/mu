<script setup lang="ts">
import { FileUp, GitBranch, ListChecks, Send, Sparkles, UploadCloud } from 'lucide-vue-next'

defineProps<{
  question: string
  selectedFileName: string
  resourceTypes: string[]
  selectedResourceTypes: string[]
  isLoading: boolean
  loadingAction: string
}>()

defineEmits<{
  'update:question': [value: string]
  'update:selectedResourceTypes': [value: string[]]
  'select-file': [event: Event]
  upload: []
  generate: []
  assess: []
  viewPath: []
  send: []
}>()

function toggleResourceType(type: string, selectedTypes: string[], emit: (event: 'update:selectedResourceTypes', value: string[]) => void) {
  const next = selectedTypes.includes(type)
    ? selectedTypes.filter((item) => item !== type)
    : [...selectedTypes, type]
  emit('update:selectedResourceTypes', next)
}
</script>

<template>
  <form class="workspace-composer" aria-label="AI tutor composer" @submit.prevent="$emit('send')">
    <div class="composer-card">
      <textarea
        :value="question"
        data-test="rag-question-input"
        rows="2"
        placeholder="问 AI Tutor：极限为什么可以描述“无限接近”？"
        @input="$emit('update:question', ($event.target as HTMLTextAreaElement).value)"
      ></textarea>

      <div class="composer-toolbar">
        <div class="quick-actions">
          <label class="quick-button file-action">
            <FileUp :size="16" aria-hidden="true" />
            <span>{{ selectedFileName || '上传资料' }}</span>
            <input
              type="file"
              data-test="document-file-input"
              accept=".md,.markdown,.pdf,.txt"
              @change="$emit('select-file', $event)"
            />
          </label>
          <button class="quick-button" type="button" data-test="upload-document" @click="$emit('upload')">
            <UploadCloud :size="16" aria-hidden="true" />
            入库
          </button>
          <button
            class="quick-button"
            type="button"
            data-test="generate-resources"
            :disabled="isLoading"
            @click="$emit('generate')"
          >
            <Sparkles :size="16" aria-hidden="true" />
            {{ loadingAction === 'resources' ? '生成中' : '生成资源' }}
          </button>
          <button
            class="quick-button"
            type="button"
            data-test="submit-assessment"
            :disabled="isLoading"
            @click="$emit('assess')"
          >
            <ListChecks :size="16" aria-hidden="true" />
            开始测评
          </button>
          <button class="quick-button" type="button" @click="$emit('viewPath')">
            <GitBranch :size="16" aria-hidden="true" />
            查看路径
          </button>
        </div>

        <button
          class="send-button"
          type="button"
          data-test="ask-rag"
          :disabled="loadingAction === 'rag'"
          @click="$emit('send')"
          aria-label="发送问题"
        >
          <Send :size="17" aria-hidden="true" />
          <span class="sr-only">Run RAG Chat</span>
          {{ loadingAction === 'rag' ? '发送中' : '发送' }}
        </button>
      </div>

      <div class="resource-type-row" aria-label="Resource type filters">
        <button
          v-for="type in resourceTypes"
          :key="type"
          :class="['type-chip', { selected: selectedResourceTypes.includes(type) }]"
          type="button"
          :data-test="`composer-resource-type-${type}`"
          :aria-pressed="selectedResourceTypes.includes(type)"
          @click="toggleResourceType(type, selectedResourceTypes, $emit)"
        >
          {{ type }}
        </button>
      </div>
    </div>
  </form>
</template>

<style scoped>
.workspace-composer {
  position: sticky;
  bottom: 0;
  z-index: 5;
  width: min(100%, 900px);
  margin: -146px auto 0;
  padding: 0 24px 24px;
  pointer-events: none;
}

.composer-card {
  display: grid;
  gap: 10px;
  padding: 12px;
  pointer-events: auto;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid #dbe3ef;
  border-radius: 22px;
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.13);
  backdrop-filter: blur(18px);
}

.composer-card textarea {
  width: 100%;
  min-height: 58px;
  max-height: 170px;
  padding: 6px 8px;
  color: #111827;
  font: inherit;
  font-size: 15px;
  line-height: 1.55;
  resize: vertical;
  background: transparent;
  border: 0;
}

.composer-card textarea:focus {
  outline: 0;
}

.composer-toolbar,
.quick-actions,
.resource-type-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.composer-toolbar {
  justify-content: space-between;
}

.quick-button,
.send-button,
.type-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 34px;
  color: #334155;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  cursor: pointer;
}

.quick-button {
  padding: 0 11px;
}

.file-action {
  position: relative;
  overflow: hidden;
  max-width: 220px;
}

.file-action input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.file-action span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.send-button {
  min-width: 92px;
  padding: 0 16px;
  color: #ffffff;
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  border-color: #4f46e5;
}

.type-chip {
  min-height: 28px;
  padding: 0 9px;
  color: #64748b;
  font-size: 11px;
}

.type-chip.selected {
  color: #4f46e5;
  background: #eef2ff;
  border-color: #c7d2fe;
}

.sr-only {
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

@media (max-width: 720px) {
  .workspace-composer {
    padding: 0 14px 16px;
  }

  .composer-toolbar {
    align-items: stretch;
  }

  .quick-actions,
  .send-button {
    width: 100%;
  }

  .quick-button,
  .send-button {
    flex: 1 1 auto;
  }
}
</style>
