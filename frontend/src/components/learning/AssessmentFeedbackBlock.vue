<script setup lang="ts">
import { ClipboardCheck } from 'lucide-vue-next'

defineProps<{
  score: number
  status: string
  replanRecordId: string
  assessmentAnswer: string
}>()

defineEmits<{
  'update:assessmentAnswer': [value: string]
}>()

function displayReplanRecordId(replanRecordId: string) {
  return replanRecordId === 'Not created' ? '未创建' : replanRecordId
}
</script>

<template>
  <article class="stream-block assessment-block">
    <div class="block-heading">
      <div>
        <p class="eyebrow">测评反馈</p>
        <h3>测评反馈卡片</h3>
      </div>
      <ClipboardCheck :size="19" aria-hidden="true" />
    </div>

    <section class="feedback-stats" aria-label="Assessment stats">
      <article>
        <span>分数</span>
        <strong>{{ score }}</strong>
      </article>
      <article>
        <span>掌握度变化</span>
        <strong>+{{ Math.max(score - 42, 0) }}%</strong>
      </article>
      <article>
        <span>正确率</span>
        <strong>{{ score }}%</strong>
      </article>
      <article>
        <span>重规划</span>
        <strong>{{ displayReplanRecordId(replanRecordId) }}</strong>
      </article>
    </section>

    <p class="feedback-copy">{{ status }}</p>
    <label class="answer-editor">
      <span>测评作答内容</span>
      <textarea
        :value="assessmentAnswer"
        data-test="assessment-answer-input"
        rows="3"
        placeholder="提交测评前，可以在这里写下你的推理过程。"
        @input="$emit('update:assessmentAnswer', ($event.target as HTMLTextAreaElement).value)"
      ></textarea>
    </label>
    <p class="suggestion">学习建议：先用一个左右趋近的例子描述“无限接近但不一定取到”，再进入 ε-δ 形式化定义。</p>
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

.feedback-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.feedback-stats article {
  display: grid;
  gap: 3px;
  min-width: 0;
  padding: 11px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.feedback-stats span,
.answer-editor span {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.feedback-stats strong {
  color: #111827;
  font-size: 18px;
  line-height: 1.2;
  overflow-wrap: anywhere;
}

.feedback-copy,
.suggestion {
  color: #475569;
  font-size: 14px;
  line-height: 1.5;
}

.suggestion {
  padding: 12px;
  background: #f5f3ff;
  border: 1px solid #ddd6fe;
  border-radius: 8px;
}

.answer-editor {
  display: grid;
  gap: 7px;
}

.answer-editor textarea {
  width: 100%;
  padding: 11px 12px;
  color: #111827;
  font: inherit;
  resize: vertical;
  background: #fbfcfe;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
}

@media (max-width: 860px) {
  .feedback-stats {
    grid-template-columns: 1fr;
  }
}
</style>
