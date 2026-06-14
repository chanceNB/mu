<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  AlertTriangle,
  CheckCircle2,
  ClipboardCheck,
  FileSearch,
  RefreshCw,
  ShieldAlert,
  ShieldCheck,
  XCircle,
} from 'lucide-vue-next'
import { decideResourceReview, listResourceReviews } from '../../api/reviews'
import type { ResourceReviewSummary, ReviewDecisionPayload } from '../../types/api'

const reviews = ref<ResourceReviewSummary[]>([])
const isLoading = ref(false)
const decidingReviewId = ref('')
const errorMessage = ref('')
const selectedReviewId = ref('')
const feedbackDraft = ref('')
const reviewStatusFilter = ref('PENDING_CRITIC')

const pendingCount = computed(
  () => reviews.value.filter((review) => review.status === 'PENDING_CRITIC').length,
)

const selectedReview = computed(
  () => reviews.value.find((review) => review.reviewId === selectedReviewId.value) ?? reviews.value[0] ?? null,
)

const reviewEvidence = computed(() => [
  {
    label: 'PlannerAgent',
    value: selectedReview.value?.generationTaskId ?? '等待资源',
    status: selectedReview.value ? 'READY' : 'IDLE',
  },
  {
    label: 'ResourceAgent',
    value: selectedReview.value?.resourceId ?? '暂无激活资源',
    status: selectedReview.value ? 'READY' : 'IDLE',
  },
  {
    label: 'CriticAgent',
    value: selectedReview.value?.resourceReviewStatus ?? selectedReview.value?.status ?? '尚未选择审核项',
    status: selectedReview.value?.status ?? 'IDLE',
  },
  {
    label: 'SafetyAgent',
    value: '发布前需要教师审核',
    status: selectedReview.value ? 'CHECK' : 'IDLE',
  },
])

onMounted(() => {
  void loadReviews()
})

async function loadReviews() {
  isLoading.value = true
  errorMessage.value = ''
  try {
    reviews.value = await listResourceReviews(reviewStatusFilter.value)
    selectReview(reviews.value[0] ?? null)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '无法加载审核队列'
  } finally {
    isLoading.value = false
  }
}

async function decide(review: ResourceReviewSummary, decision: ReviewDecisionPayload['decision']) {
  decidingReviewId.value = review.reviewId
  errorMessage.value = ''
  const feedback = feedbackDraft.value.trim()
  const payload: ReviewDecisionPayload = {
    decision,
    summary:
      feedback ||
      (decision === 'APPROVED'
        ? '教师已确认 Citation grounding、学习者匹配和安全性。'
        : '教师要求补强 Citation 或学习者匹配修订。'),
  }
  try {
    const updated = await decideResourceReview(review.reviewId, payload)
    reviews.value = reviews.value.filter((item) => item.reviewId !== updated.reviewId)
    selectReview(reviews.value[0] ?? null)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '无法提交审核决策'
  } finally {
    decidingReviewId.value = ''
  }
}

function resourceLabel(review: ResourceReviewSummary): string {
  return review.resourceTitle ?? review.resourceId
}

function selectReview(review: ResourceReviewSummary | null) {
  selectedReviewId.value = review?.reviewId ?? ''
  feedbackDraft.value = review?.summary ?? ''
}
</script>

<template>
  <section class="workspace secondary-workspace" aria-label="教师审核队列">
    <header class="workspace-header secondary-page-header">
      <div>
        <p class="eyebrow">教师审核</p>
        <h2>教师审核队列</h2>
        <p class="header-note">
          在学习资源发布给学生前审核 AI 生成内容。Citation、安全性和学习者匹配证据会展示在决策区域旁边。
        </p>
      </div>
      <div class="header-actions">
        <label class="compact-select">
          <span>状态</span>
          <select v-model="reviewStatusFilter" data-test="review-status-filter" @change="loadReviews">
            <option value="PENDING_CRITIC">待审核</option>
            <option value="APPROVED">已通过</option>
            <option value="REVISION_REQUESTED">需要修订</option>
          </select>
        </label>
        <button class="primary-action" type="button" :disabled="isLoading" @click="loadReviews">
          <RefreshCw :size="18" aria-hidden="true" />
          {{ isLoading ? '正在刷新队列' : '刷新队列' }}
        </button>
      </div>
    </header>

    <section class="metric-row review-metrics" aria-label="审核队列摘要">
      <article>
        <span>待审核资源</span>
        <strong>{{ pendingCount }}</strong>
        <p>数据来自 /api/reviews/resources</p>
      </article>
      <article>
        <span>当前决策</span>
        <strong>{{ selectedReview ? resourceLabel(selectedReview) : '无' }}</strong>
        <p>{{ selectedReview?.generationTaskId ?? '请从队列中选择一项' }}</p>
      </article>
      <article>
        <span>决策 API</span>
        <strong>已启用</strong>
        <p>APPROVED 和 REVISION_REQUESTED 仍由后端处理。</p>
      </article>
    </section>

    <section class="review-layout review-workspace secondary-split" data-test="teacher-review-workspace">
      <article class="panel queue-panel review-card">
        <div class="panel-heading">
          <div>
            <p class="eyebrow">等待审核</p>
            <h3>资源队列</h3>
          </div>
          <ShieldCheck :size="20" aria-hidden="true" />
        </div>
        <p v-if="errorMessage" class="error-text" role="status">{{ errorMessage }}</p>
        <p v-if="!isLoading && reviews.length === 0" class="answer-text">没有匹配该状态的资源。</p>
        <p v-else-if="isLoading && reviews.length === 0" class="answer-text">正在从治理 API 加载审核队列。</p>
        <ul v-else class="document-list review-queue-list">
          <li
            v-for="review in reviews"
            :key="review.reviewId"
            :class="{ selected: review.reviewId === selectedReview?.reviewId }"
          >
            <ClipboardCheck :size="16" aria-hidden="true" />
            <button
              class="review-select"
              type="button"
              :data-test="`select-review-${review.reviewId}`"
              @click="selectReview(review)"
            >
              <strong>{{ resourceLabel(review) }}</strong>
              <span>{{ review.resourceType ?? 'RESOURCE' }} / {{ review.generationTaskId }}</span>
              <span>{{ review.summary }}</span>
            </button>
            <em :class="['status-pill', review.status.toLowerCase()]">{{ review.status }}</em>
          </li>
        </ul>
      </article>

      <div class="review-detail-stack">
        <article class="panel review-panel">
          <div class="panel-heading">
            <div>
              <p class="eyebrow">当前资源</p>
              <h3>{{ selectedReview ? resourceLabel(selectedReview) : '暂无待审核资源' }}</h3>
            </div>
            <ClipboardCheck :size="20" aria-hidden="true" />
          </div>
          <section v-if="selectedReview" class="review-detail decision-surface" data-test="review-detail">
            <strong class="review-detail-title">{{ resourceLabel(selectedReview) }}</strong>
            <dl class="detail-grid review-detail-grid">
              <div>
                <dt>reviewId</dt>
                <dd>{{ selectedReview.reviewId }}</dd>
              </div>
              <div>
                <dt>resourceId</dt>
                <dd>{{ selectedReview.resourceId }}</dd>
              </div>
              <div>
                <dt>generationTaskId</dt>
                <dd>{{ selectedReview.generationTaskId }}</dd>
              </div>
              <div>
                <dt>状态</dt>
                <dd>{{ selectedReview.status }}</dd>
              </div>
            </dl>
            <p class="answer-text">{{ selectedReview.summary }}</p>
            <label class="field-control">
              <span>教师反馈</span>
              <textarea
                v-model="feedbackDraft"
                data-test="review-feedback-input"
                rows="4"
                placeholder="简要说明该资源为何可以通过，或需要修改什么。"
              ></textarea>
            </label>
            <div class="review-actions detail-actions">
              <button
                class="tool-button"
                type="button"
                data-test="approve-selected-review"
                :disabled="decidingReviewId === selectedReview.reviewId"
                @click="decide(selectedReview, 'APPROVED')"
              >
                <CheckCircle2 :size="15" aria-hidden="true" />
                通过
              </button>
              <button
                class="tool-button warning"
                type="button"
                data-test="request-revision"
                :disabled="decidingReviewId === selectedReview.reviewId"
                @click="decide(selectedReview, 'REVISION_REQUESTED')"
              >
                <XCircle :size="15" aria-hidden="true" />
                要求修改
              </button>
              <button
                class="tool-button danger"
                type="button"
                data-test="reject-review"
                disabled
                title="拒绝按钮仅用于展示工作流；当前后端契约只接受通过或修订。"
              >
                <ShieldAlert :size="15" aria-hidden="true" />
                拒绝
              </button>
            </div>
          </section>
          <section v-else class="review-detail decision-surface" data-test="review-detail">
            <p class="answer-text">暂无待审核资源。选择审核项之前，决策按钮会保持禁用。</p>
            <div class="review-actions detail-actions">
              <button class="tool-button" type="button" disabled>
                <CheckCircle2 :size="15" aria-hidden="true" />
                通过
              </button>
              <button class="tool-button warning" type="button" disabled>
                <XCircle :size="15" aria-hidden="true" />
                要求修改
              </button>
              <button class="tool-button danger" type="button" data-test="reject-review" disabled>
                <ShieldAlert :size="15" aria-hidden="true" />
                拒绝
              </button>
            </div>
          </section>
        </article>

        <section class="review-evidence-grid">
          <article class="panel">
            <div class="panel-heading">
              <div>
                <p class="eyebrow">Citation 来源</p>
                <h3>Grounding 检查</h3>
              </div>
              <FileSearch :size="20" aria-hidden="true" />
            </div>
            <ul class="rubric-list evidence-checklist" data-test="teacher-evidence-checklist">
              <li><CheckCircle2 :size="16" aria-hidden="true" /> 课程 Citation 存在且可追溯。</li>
              <li><CheckCircle2 :size="16" aria-hidden="true" /> 资源匹配当前学习路径节点和学习者薄弱点。</li>
              <li><CheckCircle2 :size="16" aria-hidden="true" /> 练习指导避免不安全捷径。</li>
              <li><AlertTriangle :size="16" aria-hidden="true" /> 无来源声明或缺少页码证据时需要修订。</li>
            </ul>
          </article>

          <article class="panel">
            <div class="panel-heading">
              <div>
                <p class="eyebrow">Critic 审核</p>
                <h3>证据链</h3>
              </div>
              <ShieldCheck :size="20" aria-hidden="true" />
            </div>
            <ul class="document-list compact-evidence-list">
              <li v-for="item in reviewEvidence" :key="item.label">
                <ShieldCheck :size="16" aria-hidden="true" />
                <div>
                  <strong>{{ item.label }}</strong>
                  <span>{{ item.value }}</span>
                </div>
                <em :class="['status-pill', item.status.toLowerCase()]">{{ item.status }}</em>
              </li>
            </ul>
          </article>
        </section>
      </div>
    </section>
  </section>
</template>
