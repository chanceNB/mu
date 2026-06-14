<script setup lang="ts">
import AiMessageBlock from '../learning/AiMessageBlock.vue'
import AssessmentFeedbackBlock from '../learning/AssessmentFeedbackBlock.vue'
import LearningPathBlock from '../learning/LearningPathBlock.vue'
import ResourceCardsBlock from '../learning/ResourceCardsBlock.vue'
import UserMessageBlock from '../learning/UserMessageBlock.vue'
import type { CitationSource, GeneratedResource, PathNode } from '../../types/api'

defineProps<{
  question: string
  profilePrompt: string
  answer: string
  stage: string
  traceId: string
  sources: CitationSource[]
  pathNodes: PathNode[]
  resources: GeneratedResource[]
  resourceTaskStatus: string
  resourceReviewStatus: string
  resourceProgressPercent: number
  resourceSafetyStatus: string
  mastery: number
  assessmentStatus: string
  assessmentAnswer: string
  replanRecordId: string
  errorMessage: string
}>()

defineEmits<{
  'update:assessmentAnswer': [value: string]
}>()
</script>

<template>
  <section class="workspace-stream" aria-label="AI 导师对话流">
    <UserMessageBlock :question="question" :profile-prompt="profilePrompt" />
    <AiMessageBlock
      :answer="answer"
      :stage="stage"
      :trace-id="traceId"
      :sources="sources"
      :error-message="errorMessage"
    />
    <LearningPathBlock id="learning-path-stream-block" :nodes="pathNodes" />
    <ResourceCardsBlock
      :resources="resources"
      :task-status="resourceTaskStatus"
      :review-status="resourceReviewStatus"
      :progress-percent="resourceProgressPercent"
      :safety-status="resourceSafetyStatus"
    />
    <AssessmentFeedbackBlock
      :score="mastery"
      :status="assessmentStatus"
      :assessment-answer="assessmentAnswer"
      :replan-record-id="replanRecordId"
      @update:assessment-answer="$emit('update:assessmentAnswer', $event)"
    />
  </section>
</template>

<style scoped>
.workspace-stream {
  display: grid;
  gap: 16px;
  width: min(100%, 900px);
  margin: 0 auto;
  padding: 24px 24px 170px;
}

@media (max-width: 720px) {
  .workspace-stream {
    padding: 18px 14px 190px;
  }
}
</style>
