<script setup lang="ts">
import { FileText, PlayCircle } from 'lucide-vue-next'
import type { GeneratedResource } from '../../types/api'

defineProps<{
  resources: GeneratedResource[]
  taskStatus: string
  reviewStatus: string
  progressPercent: number
  safetyStatus: string
}>()

const typeLabels: Record<string, string> = {
  LECTURE: '微课视频',
  EXERCISE: '练习题',
  READING: '知识总结',
  MIND_MAP: '知识总结',
  CODE_LAB: '错题解析',
}

function resourceTypeLabel(type: string) {
  return typeLabels[type] ?? type
}

function resourceMeta(resource: GeneratedResource) {
  if (resource.type === 'EXERCISE') return '6 questions'
  if (resource.type === 'CODE_LAB') return '1 lab'
  return '8 min'
}
</script>

<template>
  <article class="stream-block resource-block">
    <div class="block-heading">
      <div>
        <p class="eyebrow">Generated Resources</p>
        <h3>生成资源卡片</h3>
      </div>
      <FileText :size="19" aria-hidden="true" />
    </div>

    <dl class="resource-task-summary" data-test="resource-task-summary">
      <div>
        <dt>任务</dt>
        <dd>{{ taskStatus }}</dd>
      </div>
      <div>
        <dt>审核</dt>
        <dd>{{ reviewStatus }}</dd>
      </div>
      <div>
        <dt>进度</dt>
        <dd>{{ progressPercent }}%</dd>
      </div>
      <div>
        <dt>安全</dt>
        <dd>{{ safetyStatus }}</dd>
      </div>
    </dl>

    <div v-if="resources.length" class="resource-grid">
      <section v-for="resource in resources" :key="resource.resourceId" class="resource-card">
        <div class="resource-icon">
          <PlayCircle :size="17" aria-hidden="true" />
        </div>
        <div>
          <span class="resource-type">{{ resourceTypeLabel(resource.type) }}</span>
          <h4>{{ resource.title }}</h4>
          <p>{{ resource.citationSummary || '等待引用摘要' }}</p>
          <div class="resource-meta">
            <span>{{ resourceMeta(resource) }}</span>
            <span>{{ resource.reviewStatus }}</span>
            <span>{{ resource.safetyStatus }}</span>
          </div>
        </div>
      </section>
    </div>

    <p v-else class="empty-state">还没有生成资源。点击底部 composer 的“生成资源”后，这里会展示微课、练习题、知识总结和错题解析。</p>
  </article>
</template>

<style scoped>
.stream-block {
  display: grid;
  gap: 14px;
  padding: 18px;
  background: #ffffff;
  border: 1px solid #e6ebf2;
  border-radius: 8px;
}

.block-heading {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.eyebrow {
  color: #6366f1;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0;
  text-transform: uppercase;
}

.block-heading h3 {
  margin: 4px 0 0;
  color: #111827;
  font-size: 17px;
  letter-spacing: 0;
}

.block-heading svg {
  color: #6366f1;
}

.resource-task-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
}

.resource-task-summary div {
  min-width: 0;
  padding: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.resource-task-summary dt {
  color: #64748b;
  font-size: 11px;
  font-weight: 800;
}

.resource-task-summary dd {
  margin: 3px 0 0;
  color: #111827;
  font-size: 13px;
  font-weight: 800;
  overflow-wrap: anywhere;
}

.resource-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.resource-card {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  gap: 11px;
  min-width: 0;
  padding: 14px;
  background: #fbfcfe;
  border: 1px solid #e3e9f2;
  border-radius: 8px;
  transition: border-color 180ms ease, transform 180ms ease, box-shadow 180ms ease;
}

.resource-card:hover {
  border-color: #c7d2fe;
  transform: translateY(-1px);
  box-shadow: 0 10px 20px rgba(15, 23, 42, 0.06);
}

.resource-icon {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  color: #4f46e5;
  background: #eef2ff;
  border-radius: 8px;
}

.resource-type {
  color: #6366f1;
  font-size: 12px;
  font-weight: 800;
}

.resource-card h4 {
  margin: 3px 0 4px;
  color: #111827;
  font-size: 15px;
  line-height: 1.3;
  letter-spacing: 0;
}

.resource-card p,
.empty-state {
  color: #64748b;
  font-size: 13px;
  line-height: 1.45;
}

.resource-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.resource-meta span {
  padding: 4px 7px;
  color: #475569;
  font-size: 11px;
  font-weight: 800;
  background: #eef2f7;
  border-radius: 999px;
}

@media (max-width: 860px) {
  .resource-task-summary,
  .resource-grid {
    grid-template-columns: 1fr;
  }
}
</style>
