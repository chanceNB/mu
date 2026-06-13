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
    value: selectedReview.value?.generationTaskId ?? 'Waiting for resource',
    status: selectedReview.value ? 'READY' : 'IDLE',
  },
  {
    label: 'ResourceAgent',
    value: selectedReview.value?.resourceId ?? 'No active resource',
    status: selectedReview.value ? 'READY' : 'IDLE',
  },
  {
    label: 'CriticAgent',
    value: selectedReview.value?.resourceReviewStatus ?? selectedReview.value?.status ?? 'No review selected',
    status: selectedReview.value?.status ?? 'IDLE',
  },
  {
    label: 'SafetyAgent',
    value: 'Teacher gate required before release',
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
    errorMessage.value = error instanceof Error ? error.message : 'Unable to load review queue'
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
        ? 'Teacher approved citation grounding, learner fit, and safety.'
        : 'Teacher requested stronger citations or learner-fit revisions.'),
  }
  try {
    const updated = await decideResourceReview(review.reviewId, payload)
    reviews.value = reviews.value.filter((item) => item.reviewId !== updated.reviewId)
    selectReview(reviews.value[0] ?? null)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Unable to submit review decision'
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
  <section class="workspace secondary-workspace" aria-label="Teacher review queue">
    <header class="workspace-header secondary-page-header">
      <div>
        <p class="eyebrow">Teacher Review</p>
        <h2>Teacher Review Queue</h2>
        <p class="header-note">
          Review AI generated learning resources before they reach students. Citation, safety, and learner-fit evidence stay visible next to the decision surface.
        </p>
      </div>
      <div class="header-actions">
        <label class="compact-select">
          <span>Status</span>
          <select v-model="reviewStatusFilter" data-test="review-status-filter" @change="loadReviews">
            <option value="PENDING_CRITIC">Pending critic</option>
            <option value="APPROVED">Approved</option>
            <option value="REVISION_REQUESTED">Needs revision</option>
          </select>
        </label>
        <button class="primary-action" type="button" :disabled="isLoading" @click="loadReviews">
          <RefreshCw :size="18" aria-hidden="true" />
          {{ isLoading ? 'Refreshing queue' : 'Refresh queue' }}
        </button>
      </div>
    </header>

    <section class="metric-row review-metrics" aria-label="Review queue summary">
      <article>
        <span>Pending resources</span>
        <strong>{{ pendingCount }}</strong>
        <p>Loaded from /api/reviews/resources</p>
      </article>
      <article>
        <span>Selected decision</span>
        <strong>{{ selectedReview ? resourceLabel(selectedReview) : 'None' }}</strong>
        <p>{{ selectedReview?.generationTaskId ?? 'Select an item from the queue' }}</p>
      </article>
      <article>
        <span>Decision API</span>
        <strong>Active</strong>
        <p>APPROVED and REVISION_REQUESTED remain backend-backed.</p>
      </article>
    </section>

    <section class="review-layout review-workspace secondary-split" data-test="teacher-review-workspace">
      <article class="panel queue-panel review-card">
        <div class="panel-heading">
          <div>
            <p class="eyebrow">Awaiting review</p>
            <h3>Resource queue</h3>
          </div>
          <ShieldCheck :size="20" aria-hidden="true" />
        </div>
        <p v-if="errorMessage" class="error-text" role="status">{{ errorMessage }}</p>
        <p v-if="!isLoading && reviews.length === 0" class="answer-text">No resources match this status.</p>
        <p v-else-if="isLoading && reviews.length === 0" class="answer-text">Loading review queue from governance APIs.</p>
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
              <p class="eyebrow">Current resource</p>
              <h3>{{ selectedReview ? resourceLabel(selectedReview) : 'No pending resource' }}</h3>
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
                <dt>Status</dt>
                <dd>{{ selectedReview.status }}</dd>
              </div>
            </dl>
            <p class="answer-text">{{ selectedReview.summary }}</p>
            <label class="field-control">
              <span>Teacher feedback</span>
              <textarea
                v-model="feedbackDraft"
                data-test="review-feedback-input"
                rows="4"
                placeholder="Summarize why this resource should pass or what needs to change."
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
                Approve
              </button>
              <button
                class="tool-button warning"
                type="button"
                data-test="request-revision"
                :disabled="decidingReviewId === selectedReview.reviewId"
                @click="decide(selectedReview, 'REVISION_REQUESTED')"
              >
                <XCircle :size="15" aria-hidden="true" />
                Request changes
              </button>
              <button
                class="tool-button danger"
                type="button"
                data-test="reject-review"
                disabled
                title="Reject is displayed for the workflow, but the current backend contract only accepts approve or revision."
              >
                <ShieldAlert :size="15" aria-hidden="true" />
                Reject
              </button>
            </div>
          </section>
          <section v-else class="review-detail decision-surface" data-test="review-detail">
            <p class="answer-text">No pending resources. Decision buttons stay disabled until a review is selected.</p>
            <div class="review-actions detail-actions">
              <button class="tool-button" type="button" disabled>
                <CheckCircle2 :size="15" aria-hidden="true" />
                Approve
              </button>
              <button class="tool-button warning" type="button" disabled>
                <XCircle :size="15" aria-hidden="true" />
                Request changes
              </button>
              <button class="tool-button danger" type="button" data-test="reject-review" disabled>
                <ShieldAlert :size="15" aria-hidden="true" />
                Reject
              </button>
            </div>
          </section>
        </article>

        <section class="review-evidence-grid">
          <article class="panel">
            <div class="panel-heading">
              <div>
                <p class="eyebrow">Citation source</p>
                <h3>Grounding check</h3>
              </div>
              <FileSearch :size="20" aria-hidden="true" />
            </div>
            <ul class="rubric-list evidence-checklist" data-test="teacher-evidence-checklist">
              <li><CheckCircle2 :size="16" aria-hidden="true" /> Course citation exists and can be traced.</li>
              <li><CheckCircle2 :size="16" aria-hidden="true" /> Resource matches the active path node and learner weakness.</li>
              <li><CheckCircle2 :size="16" aria-hidden="true" /> Exercise guidance avoids unsafe shortcuts.</li>
              <li><AlertTriangle :size="16" aria-hidden="true" /> No-source claims or missing page evidence require revision.</li>
            </ul>
          </article>

          <article class="panel">
            <div class="panel-heading">
              <div>
                <p class="eyebrow">Critic Review</p>
                <h3>Evidence chain</h3>
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
