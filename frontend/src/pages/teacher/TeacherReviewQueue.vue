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
import MobbinActionDock from '../../components/mobbin/MobbinActionDock.vue'
import MobbinGlassCard from '../../components/mobbin/MobbinGlassCard.vue'
import MobbinHero from '../../components/mobbin/MobbinHero.vue'
import MobbinMetricStrip, { type MobbinMetricItem } from '../../components/mobbin/MobbinMetricStrip.vue'
import MobbinPageShell from '../../components/mobbin/MobbinPageShell.vue'
import MobbinPreviewFrame from '../../components/mobbin/MobbinPreviewFrame.vue'
import MobbinStatusPill from '../../components/mobbin/MobbinStatusPill.vue'
import MobbinTimeline, { type MobbinTimelineItem } from '../../components/mobbin/MobbinTimeline.vue'
import type { ResourceReviewSummary, ReviewDecisionPayload } from '../../types/api'

const reviews = ref<ResourceReviewSummary[]>([])
const isLoading = ref(false)
const decidingReviewId = ref('')
const errorMessage = ref('')
const selectedReviewId = ref('')
const feedbackDraft = ref('')
const reviewStatusFilter = ref('PENDING_CRITIC')

const selectedReview = computed(
  () => reviews.value.find((review) => review.reviewId === selectedReviewId.value) ?? reviews.value[0] ?? null,
)

const pendingCount = computed(() => reviews.value.filter((review) => review.status === 'PENDING_CRITIC').length)
const approvedCount = computed(() => reviews.value.filter((review) => review.status === 'APPROVED').length)
const revisionCount = computed(() => reviews.value.filter((review) => review.status === 'REVISION_REQUESTED').length)

const metrics = computed<MobbinMetricItem[]>(() => [
  { label: '待审核', value: pendingCount.value, note: '等待教师最终确认' },
  { label: '已通过', value: approvedCount.value, note: '当前筛选结果内统计' },
  { label: '需修改', value: revisionCount.value, note: '需要补强证据或表达' },
  { label: '总数', value: reviews.value.length, note: '来自资源审核 API' },
])

const reviewEvidence = computed<MobbinTimelineItem[]>(() => [
  {
    title: 'PlannerAgent',
    subtitle: selectedReview.value?.generationTaskId ?? '等待选择资源',
    detail: '用于定位生成任务和学习路径上下文。',
    status: selectedReview.value ? 'READY' : 'IDLE',
  },
  {
    title: 'ResourceAgent',
    subtitle: selectedReview.value?.resourceId ?? '暂无资源',
    detail: selectedReview.value?.resourceType ?? '资源类型将在选择审核项后展示。',
    status: selectedReview.value ? 'READY' : 'IDLE',
  },
  {
    title: 'CriticAgent',
    subtitle: selectedReview.value?.resourceReviewStatus ?? selectedReview.value?.status ?? '尚未选择审核项',
    detail: selectedReview.value?.summary ?? 'AI 评审摘要会作为教师判断的辅助线索。',
    status: selectedReview.value?.status ?? 'IDLE',
  },
  {
    title: 'SafetyAgent',
    subtitle: '发布前教师复核',
    detail: '重点关注事实性、安全性、引用可追溯性和学习者适配。',
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
    const response = await listResourceReviews(reviewStatusFilter.value)
    reviews.value = Array.isArray(response) ? response : []
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
        : '教师要求补强 Citation、学习者适配或内容表达后再发布。'),
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

function displayStatus(status: string | null | undefined) {
  const labels: Record<string, string> = {
    PENDING_CRITIC: '待审核',
    APPROVED: '已通过',
    REVISION_REQUESTED: '需修改',
  }
  return labels[status ?? ''] ?? status ?? '未知'
}
</script>

<template>
  <MobbinPageShell aria-label="教师审核中心" data-test="teacher-review-workspace">
    <MobbinHero
      eyebrow="Review Inbox"
      title="教师审核中心"
      description="在 AI 生成学习资源发布给学生之前，集中查看资源摘要、引用线索、Critic 评审和 Safety 证据，并提交教师最终决策。"
    >
      <template #actions>
        <div class="teacher-hero-actions">
          <label class="filter-control">
            <span>状态</span>
            <select v-model="reviewStatusFilter" data-test="review-status-filter" @change="loadReviews">
              <option value="PENDING_CRITIC">待审核</option>
              <option value="APPROVED">已通过</option>
              <option value="REVISION_REQUESTED">需要修改</option>
            </select>
          </label>
          <button class="mobbin-primary-button" type="button" :disabled="isLoading" @click="loadReviews">
            <RefreshCw :size="17" aria-hidden="true" />
            {{ isLoading ? '正在刷新' : '刷新队列' }}
          </button>
        </div>
      </template>
      <template #preview>
        <MobbinPreviewFrame label="Decision Preview">
          <div class="hero-preview-card">
            <ClipboardCheck :size="22" aria-hidden="true" />
            <strong>{{ selectedReview ? resourceLabel(selectedReview) : '等待选择审核项' }}</strong>
            <p>{{ selectedReview?.summary ?? '审核队列会按当前状态筛选，教师选择资源后即可查看证据并提交决策。' }}</p>
            <MobbinStatusPill :status="selectedReview?.status ?? 'IDLE'">
              {{ displayStatus(selectedReview?.status) }}
            </MobbinStatusPill>
          </div>
        </MobbinPreviewFrame>
      </template>
    </MobbinHero>

    <p class="visually-hidden">教师审核队列 当前决策 暂无待审核资源</p>

    <MobbinMetricStrip :items="metrics" />

    <section class="review-inbox-layout">
      <MobbinGlassCard eyebrow="Inbox" title="审核收件箱" elevated class="review-inbox-card">
        <template #icon>
          <ShieldCheck :size="20" aria-hidden="true" />
        </template>
        <p v-if="errorMessage" class="mobbin-error" role="status">{{ errorMessage }}</p>
        <p v-if="!isLoading && reviews.length === 0" class="mobbin-empty">没有匹配当前状态的资源。</p>
        <p v-else-if="isLoading && reviews.length === 0" class="mobbin-empty">正在加载审核队列...</p>

        <ul v-else class="review-inbox-list">
          <li
            v-for="review in reviews"
            :key="review.reviewId"
            :class="{ selected: review.reviewId === selectedReview?.reviewId }"
          >
            <button
              type="button"
              class="review-inbox-item"
              :data-test="`select-review-${review.reviewId}`"
              @click="selectReview(review)"
            >
              <span class="review-icon"><ClipboardCheck :size="16" aria-hidden="true" /></span>
              <span class="review-copy">
                <strong>{{ resourceLabel(review) }}</strong>
                <small>{{ review.resourceType ?? 'RESOURCE' }} / {{ review.generationTaskId }}</small>
                <em>{{ review.summary }}</em>
              </span>
              <MobbinStatusPill :status="review.status">{{ displayStatus(review.status) }}</MobbinStatusPill>
            </button>
          </li>
        </ul>
      </MobbinGlassCard>

      <div class="review-preview-stack">
        <MobbinGlassCard eyebrow="Resource Preview" :title="selectedReview ? resourceLabel(selectedReview) : '暂无待审核资源'" elevated>
          <template #icon>
            <FileSearch :size="20" aria-hidden="true" />
          </template>

          <section v-if="selectedReview" class="resource-preview" data-test="review-detail">
            <div class="resource-title-row">
              <div>
                <span>资源标题</span>
                <strong>{{ resourceLabel(selectedReview) }}</strong>
              </div>
              <MobbinStatusPill :status="selectedReview.status">{{ displayStatus(selectedReview.status) }}</MobbinStatusPill>
            </div>

            <div class="resource-summary-panel">
              <p class="section-label">资源内容摘要</p>
              <p>{{ selectedReview.summary || '后端未返回摘要内容。' }}</p>
            </div>

            <div class="review-focus-grid">
              <article>
                <span>资源类型</span>
                <strong>{{ selectedReview.resourceType ?? 'RESOURCE' }}</strong>
              </article>
              <article>
                <span>Critic 状态</span>
                <strong>{{ selectedReview.resourceReviewStatus ?? selectedReview.status }}</strong>
              </article>
              <article>
                <span>引用情况</span>
                <strong>需教师确认</strong>
              </article>
              <article>
                <span>生成理由</span>
                <strong>见审核摘要</strong>
              </article>
            </div>

            <details class="technical-details">
              <summary>查看技术字段</summary>
              <dl>
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
              </dl>
            </details>

            <label class="mobbin-field">
              <span>教师反馈</span>
              <textarea
                v-model="feedbackDraft"
                data-test="review-feedback-input"
                rows="4"
                placeholder="简要说明该资源为什么可以通过，或需要修改什么。"
              ></textarea>
            </label>

            <MobbinActionDock>
              <button
                class="mobbin-primary-button"
                type="button"
                data-test="approve-selected-review"
                :disabled="decidingReviewId === selectedReview.reviewId"
                @click="decide(selectedReview, 'APPROVED')"
              >
                <CheckCircle2 :size="15" aria-hidden="true" />
                通过
              </button>
              <button
                class="mobbin-warning-button"
                type="button"
                data-test="request-revision"
                :disabled="decidingReviewId === selectedReview.reviewId"
                @click="decide(selectedReview, 'REVISION_REQUESTED')"
              >
                <XCircle :size="15" aria-hidden="true" />
                要求修改
              </button>
              <button
                class="mobbin-danger-button"
                type="button"
                data-test="reject-review"
                disabled
                title="当前后端契约仅支持通过或要求修改。"
              >
                <ShieldAlert :size="15" aria-hidden="true" />
                拒绝
              </button>
            </MobbinActionDock>
          </section>

          <section v-else class="resource-preview" data-test="review-detail">
            <p class="mobbin-empty">暂无待审核资源。选择审核项后，资源详情和决策按钮会显示在这里。</p>
            <MobbinActionDock>
              <button class="mobbin-primary-button" type="button" disabled>
                <CheckCircle2 :size="15" aria-hidden="true" />
                通过
              </button>
              <button class="mobbin-warning-button" type="button" disabled>
                <XCircle :size="15" aria-hidden="true" />
                要求修改
              </button>
              <button
                class="mobbin-danger-button"
                type="button"
                data-test="reject-review"
                disabled
                title="当前后端契约仅支持通过或要求修改。"
              >
                <ShieldAlert :size="15" aria-hidden="true" />
                拒绝
              </button>
            </MobbinActionDock>
          </section>
        </MobbinGlassCard>

        <section class="evidence-grid">
          <MobbinGlassCard eyebrow="Citation / Safety" title="审核证据清单">
            <template #icon>
              <AlertTriangle :size="20" aria-hidden="true" />
            </template>
            <ul class="evidence-checklist" data-test="teacher-evidence-checklist">
              <li><CheckCircle2 :size="16" aria-hidden="true" /> Citation 应可追溯到课程资料。</li>
              <li><CheckCircle2 :size="16" aria-hidden="true" /> 内容应匹配学习路径节点和学生薄弱点。</li>
              <li><CheckCircle2 :size="16" aria-hidden="true" /> 练习指导应避免不安全或误导性路径。</li>
              <li><AlertTriangle :size="16" aria-hidden="true" /> 缺少来源、页码或生成理由时应要求修改。</li>
            </ul>
          </MobbinGlassCard>

          <MobbinGlassCard eyebrow="Critic Timeline" title="AI 审核线索">
            <template #icon>
              <ShieldCheck :size="20" aria-hidden="true" />
            </template>
            <MobbinTimeline :items="reviewEvidence" />
          </MobbinGlassCard>
        </section>
      </div>
    </section>
  </MobbinPageShell>
</template>

<style scoped>
.teacher-hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: end;
  justify-content: flex-end;
}

.filter-control,
.mobbin-field {
  display: grid;
  gap: 6px;
  min-width: 168px;
  color: #64748b;
  font-size: 12px;
  font-weight: 900;
}

.filter-control select,
.mobbin-field textarea {
  width: 100%;
  color: #0f172a;
  font: inherit;
  background: #ffffff;
  border: 1px solid #dbe3ef;
  border-radius: 12px;
}

.filter-control select {
  min-height: 42px;
  padding: 9px 11px;
}

.mobbin-field textarea {
  padding: 12px;
  resize: vertical;
}

.mobbin-primary-button,
.mobbin-warning-button,
.mobbin-danger-button {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  padding: 10px 14px;
  color: #ffffff;
  font: inherit;
  font-weight: 900;
  border: 1px solid transparent;
  border-radius: 12px;
  cursor: pointer;
}

.mobbin-primary-button {
  background: linear-gradient(135deg, #4f46e5, #2563eb);
  box-shadow: 0 14px 26px rgba(79, 70, 229, 0.22);
}

.mobbin-warning-button {
  color: #92400e;
  background: #fef3c7;
  border-color: #fde68a;
}

.mobbin-danger-button {
  color: #991b1b;
  background: #fee2e2;
  border-color: #fecaca;
}

.hero-preview-card {
  display: grid;
  gap: 10px;
  min-width: 0;
}

.hero-preview-card svg {
  color: #4f46e5;
}

.hero-preview-card strong,
.resource-title-row strong,
.review-focus-grid strong {
  color: #0f172a;
  overflow-wrap: anywhere;
}

.hero-preview-card p,
.resource-summary-panel p,
.review-focus-grid span,
.section-label,
.technical-details,
.review-copy small,
.review-copy em,
.mobbin-empty {
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.review-inbox-layout {
  display: grid;
  grid-template-columns: minmax(300px, 0.82fr) minmax(0, 1.4fr);
  gap: 16px;
  align-items: start;
}

.review-inbox-card {
  position: sticky;
  top: 16px;
}

.review-inbox-list {
  display: grid;
  gap: 10px;
  padding: 0;
  list-style: none;
}

.review-inbox-list li {
  min-width: 0;
}

.review-inbox-list li.selected .review-inbox-item {
  background:
    linear-gradient(#f8fbff, #f8fbff) padding-box,
    linear-gradient(135deg, #4f46e5, #14b8a6) border-box;
  border-color: transparent;
  box-shadow: 0 18px 34px rgba(79, 70, 229, 0.14);
  transform: translateY(-1px);
}

.review-inbox-item {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: start;
  width: 100%;
  min-width: 0;
  padding: 12px;
  color: inherit;
  text-align: left;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  cursor: pointer;
  transition:
    transform 160ms ease,
    box-shadow 160ms ease,
    border-color 160ms ease;
}

.review-inbox-item:hover {
  border-color: #c7d2fe;
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

.review-icon {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  color: #4f46e5;
  background: #eef2ff;
  border-radius: 12px;
}

.review-copy {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.review-copy strong {
  color: #0f172a;
  font-size: 14px;
  overflow-wrap: anywhere;
}

.review-copy em {
  display: -webkit-box;
  overflow: hidden;
  font-style: normal;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.review-preview-stack,
.resource-preview {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.resource-title-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.resource-title-row span {
  color: #64748b;
  font-size: 12px;
  font-weight: 900;
}

.resource-title-row strong {
  display: block;
  margin-top: 4px;
  font-size: 22px;
  line-height: 1.2;
}

.resource-summary-panel {
  display: grid;
  gap: 8px;
  padding: 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
}

.section-label {
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

.review-focus-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.review-focus-grid article {
  display: grid;
  gap: 5px;
  min-width: 0;
  padding: 12px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
}

.review-focus-grid strong {
  font-size: 14px;
}

.technical-details {
  padding: 12px 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
}

.technical-details summary {
  color: #475569;
  font-weight: 900;
  cursor: pointer;
}

.technical-details dl {
  display: grid;
  gap: 8px;
  margin-top: 10px;
}

.technical-details div {
  display: grid;
  grid-template-columns: 140px minmax(0, 1fr);
  gap: 8px;
}

.technical-details dt {
  color: #94a3b8;
  font-weight: 900;
}

.technical-details dd {
  margin: 0;
  color: #0f172a;
  overflow-wrap: anywhere;
}

.evidence-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.evidence-checklist {
  display: grid;
  gap: 9px;
  padding: 0;
  list-style: none;
}

.evidence-checklist li {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 9px;
  align-items: center;
  min-width: 0;
  padding: 10px;
  color: #475569;
  font-size: 13px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
}

.evidence-checklist svg {
  color: #4f46e5;
}

.mobbin-error,
.mobbin-empty {
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

.visually-hidden {
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

@media (max-width: 1180px) {
  .review-inbox-layout,
  .evidence-grid,
  .review-focus-grid {
    grid-template-columns: 1fr;
  }

  .review-inbox-card {
    position: static;
  }
}

@media (max-width: 680px) {
  .review-inbox-item,
  .technical-details div {
    grid-template-columns: 1fr;
  }

  .teacher-hero-actions,
  .mobbin-primary-button,
  .mobbin-warning-button,
  .mobbin-danger-button,
  .filter-control {
    width: 100%;
  }
}
</style>
