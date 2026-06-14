<script setup lang="ts">
import {
  BarChart3,
  BookOpen,
  ClipboardCheck,
  Database,
  FileText,
  FileUp,
  GitBranch,
  ListChecks,
  RefreshCw,
  UploadCloud,
  UserRound,
} from 'lucide-vue-next'
import type { DocumentRecord, GeneratedResource, PathNode, TraceStep, WorkbenchState } from '../../types/api'

const props = defineProps<{
  state: WorkbenchState
  documents: DocumentRecord[]
  pathNodes: PathNode[]
  resources: GeneratedResource[]
  traceSteps: TraceStep[]
  selectedDocumentFileName: string
  selectedResourceTypes: string[]
  resourceTypes: string[]
  indexedDocuments: number
  pendingDocuments: number
  averageMastery: number
  approvedResources: GeneratedResource[]
  pendingReviewResources: GeneratedResource[]
  revisionResources: GeneratedResource[]
  otherReviewResources: GeneratedResource[]
  localizedErrorMessage: string
  isLoading: boolean
  displayStatus: (status: string) => string
  displayReplanRecordId: (replanRecordId: string) => string
}>()

const emit = defineEmits<{
  'update:selectedResourceTypes': [value: string[]]
  'select-file': [event: Event]
  'upload': []
  'generate': []
  'refresh-resource-status': []
  'assess': []
  'refine-profile': []
  'select-follow-up-question': [question: string]
}>()

function toggleResourceType(type: string, checked: boolean) {
  const next = checked
    ? [...new Set([...props.selectedResourceTypes, type])]
    : props.selectedResourceTypes.filter((selectedType) => selectedType !== type)
  emit('update:selectedResourceTypes', next)
}
</script>

<template>
  <section class="mobbin-showcase" aria-label="Detailed AI learning controls">
    <div class="mobbin-upgrade-banner">
      <div>
        <p class="eyebrow">Student Learning Loop</p>
        <h2>Course RAG + Agent Generation + Assessment</h2>
        <p>画像、知识库、学习路径、生成资源、测评反馈和 Agent Trace 被整理成可浏览的学习应用陈列。</p>
      </div>
      <div class="mobbin-banner-stats" aria-label="Learning summary">
        <span>{{ indexedDocuments }} indexed</span>
        <span>{{ pendingDocuments }} pending</span>
        <span>{{ averageMastery }}% mastery</span>
      </div>
    </div>

    <div class="mobbin-card-grid">
      <article class="mobbin-card" data-test="student-profile-showcase">
        <div class="mobbin-card-stage">
          <span class="mobbin-card-badge">Updated</span>
          <div class="mobbin-phone-preview profile-preview">
            <div class="phone-topbar"></div>
            <div class="profile-orb"><UserRound :size="28" aria-hidden="true" /></div>
            <strong>{{ state.learnerProfile.learnerId || 'Profile pending' }}</strong>
            <p>{{ state.learnerProfile.goal || 'No learning goal yet' }}</p>
            <div class="profile-meter">
              <span :style="{ width: `${averageMastery}%` }"></span>
            </div>
            <dl>
              <div>
                <dt>Weakness</dt>
                <dd>{{ state.learnerProfile.weakness || 'Not detected' }}</dd>
              </div>
              <div>
                <dt>Preference</dt>
                <dd>{{ state.learnerProfile.preference || 'Not detected' }}</dd>
              </div>
            </dl>
          </div>
        </div>
        <div class="mobbin-card-body">
          <label class="field-control">
            <span>画像补充信息</span>
            <textarea
              v-model="state.profilePrompt"
              data-test="profile-prompt-input"
              rows="3"
              placeholder="描述学习目标、当前卡点和资源偏好"
            ></textarea>
          </label>
          <div v-if="state.followUpQuestions.length" class="follow-up-list" aria-label="Profile follow-up questions">
            <button
              v-for="(question, index) in state.followUpQuestions"
              :key="question"
              :class="['follow-up-chip', { selected: state.selectedFollowUpQuestion === question }]"
              type="button"
              :aria-pressed="state.selectedFollowUpQuestion === question"
              :data-test="`profile-follow-up-${index}`"
              @click="emit('select-follow-up-question', question)"
            >
              {{ question }}
            </button>
          </div>
          <button class="tool-button" type="button" data-test="refine-profile" :disabled="isLoading" @click="emit('refine-profile')">
            <UserRound :size="17" aria-hidden="true" />
            {{ state.loadingAction === 'profile' ? '更新画像中' : '更新画像' }}
          </button>
        </div>
        <footer class="mobbin-card-footer">
          <span class="mobbin-app-icon profile"><UserRound :size="20" aria-hidden="true" /></span>
          <div>
            <h3>学习画像</h3>
            <p>{{ state.learnerProfile.major }} / {{ state.learnerProfile.weakness }}</p>
          </div>
        </footer>
      </article>

      <article class="mobbin-card" data-test="knowledge-base-showcase">
        <div class="mobbin-card-stage">
          <span class="mobbin-card-badge">Ready</span>
          <div class="mobbin-phone-preview kb-preview">
            <Database :size="32" aria-hidden="true" />
            <strong>{{ state.knowledgeBase.name }}</strong>
            <p>{{ documents.length }} documents / {{ state.knowledgeBase.visibility }}</p>
            <ul class="mini-document-stack">
              <li v-for="document in documents.slice(0, 3)" :key="document.id">
                <span>{{ document.name }}</span>
                <em :class="['status-pill', document.status.toLowerCase()]">{{ document.status }}</em>
              </li>
            </ul>
          </div>
        </div>
        <div class="mobbin-card-body">
          <label class="file-picker">
            <FileUp :size="17" aria-hidden="true" />
            <span>{{ selectedDocumentFileName || '选择课程资料' }}</span>
            <input type="file" data-test="document-file-input" accept=".md,.markdown,.pdf,.txt" @change="emit('select-file', $event)" />
          </label>
          <button class="tool-button" type="button" data-test="upload-document" :disabled="state.loadingAction === 'document'" @click="emit('upload')">
            <UploadCloud :size="17" aria-hidden="true" />
            {{ state.loadingAction === 'document' ? '上传中' : '上传课程资料' }}
          </button>
          <p v-if="state.errorMessage" class="error-text" role="status">{{ localizedErrorMessage }}</p>
        </div>
        <footer class="mobbin-card-footer">
          <span class="mobbin-app-icon kb"><Database :size="20" aria-hidden="true" /></span>
          <div>
            <h3>知识库</h3>
            <p>{{ indexedDocuments }} indexed, {{ pendingDocuments }} pending</p>
          </div>
        </footer>
      </article>

      <article class="mobbin-card" data-test="learning-path-showcase">
        <div class="mobbin-card-stage">
          <span class="mobbin-card-badge">进行中</span>
          <div class="mobbin-phone-preview path-preview">
            <section v-for="node in pathNodes" :key="node.id" class="mini-path-card">
              <div>
                <strong>{{ node.title }}</strong>
                <em :class="['status-pill', node.status.toLowerCase()]">{{ displayStatus(node.status) }}</em>
              </div>
              <p>{{ node.reason }}</p>
              <div class="mini-meter"><span :style="{ width: `${node.mastery}%` }"></span></div>
            </section>
          </div>
        </div>
        <footer class="mobbin-card-footer">
          <span class="mobbin-app-icon path"><BookOpen :size="20" aria-hidden="true" /></span>
          <div>
            <h3>学习路径</h3>
            <p>{{ pathNodes.length || 0 }} path nodes / {{ state.pathTraceId }}</p>
          </div>
        </footer>
      </article>

      <article class="mobbin-card" data-test="resource-showcase">
        <div class="mobbin-card-stage">
          <span class="mobbin-card-badge">{{ displayStatus(state.resourceTaskStatus) }}</span>
          <div class="mobbin-phone-preview resource-preview">
            <div class="resource-progress-ring">{{ state.resourceProgressPercent }}%</div>
            <strong>{{ state.resourceTaskId || 'No task yet' }}</strong>
            <p>{{ displayStatus(state.resourceReviewStatus) }} / {{ displayStatus(state.resourceSafetyStatus) }}</p>
            <div class="resource-mini-board">
              <span>Approved {{ approvedResources.length }}</span>
              <span>Pending {{ pendingReviewResources.length }}</span>
              <span>Revision {{ revisionResources.length }}</span>
              <span>Other {{ otherReviewResources.length }}</span>
            </div>
          </div>
        </div>
        <div class="mobbin-card-body">
          <fieldset class="resource-type-picker">
            <legend>资源类型</legend>
            <label v-for="type in resourceTypes" :key="type" class="resource-type-option">
              <input
                type="checkbox"
                :value="type"
                :checked="selectedResourceTypes.includes(type)"
                :data-test="`resource-type-${type}`"
                @change="toggleResourceType(type, ($event.target as HTMLInputElement).checked)"
              />
              <span>{{ type }}</span>
            </label>
          </fieldset>
          <div class="mobbin-action-row">
            <button class="tool-button" type="button" data-test="generate-resources" :disabled="isLoading" @click="emit('generate')">
              <ListChecks :size="17" aria-hidden="true" />
              {{ state.loadingAction === 'resources' ? 'AI 生成资源中' : 'AI 生成资源' }}
            </button>
            <button
              class="tool-button secondary"
              type="button"
              data-test="refresh-resource-status"
              :disabled="isLoading || !state.resourceTaskId"
              @click="emit('refresh-resource-status')"
            >
              <RefreshCw :size="17" aria-hidden="true" />
              {{ state.loadingAction === 'resource-status' ? '检查中' : '查看状态' }}
            </button>
          </div>
          <dl class="resource-task-summary" data-test="resource-task-summary">
            <div><dt>任务</dt><dd>{{ displayStatus(state.resourceTaskStatus) }}</dd></div>
            <div><dt>审核</dt><dd>{{ displayStatus(state.resourceReviewStatus) }}</dd></div>
            <div><dt>进度</dt><dd>{{ state.resourceProgressPercent }}%</dd></div>
            <div><dt>安全</dt><dd>{{ displayStatus(state.resourceSafetyStatus) }}</dd></div>
          </dl>
        </div>
        <footer class="mobbin-card-footer">
          <span class="mobbin-app-icon resource"><FileText :size="20" aria-hidden="true" /></span>
          <div>
            <h3>生成资源</h3>
            <p>{{ resources.length }} resources / {{ state.resourceTraceId }}</p>
          </div>
        </footer>
      </article>

      <article class="mobbin-card" data-test="assessment-showcase">
        <div class="mobbin-card-stage">
          <span class="mobbin-card-badge">Updated</span>
          <div class="mobbin-phone-preview assessment-preview">
            <BarChart3 :size="30" aria-hidden="true" />
            <strong>{{ state.mastery }}%</strong>
            <p>{{ state.assessmentStatus }}</p>
            <div class="mastery-meter"><span :style="{ width: `${state.mastery}%` }"></span></div>
            <small>Replan {{ displayReplanRecordId(state.replanRecordId) }}</small>
          </div>
        </div>
        <div class="mobbin-card-body">
          <label class="field-control">
            <span>作答内容</span>
            <textarea
              v-model="state.assessmentAnswer"
              data-test="assessment-answer-input"
              rows="3"
              placeholder="提交前请说明你的推理过程"
            ></textarea>
          </label>
          <button class="tool-button" type="button" data-test="submit-assessment" :disabled="isLoading" @click="emit('assess')">
            <ClipboardCheck :size="17" aria-hidden="true" />
            {{ state.loadingAction === 'assessment' ? '提交中' : '开始测评' }}
          </button>
        </div>
        <footer class="mobbin-card-footer">
          <span class="mobbin-app-icon assessment"><ClipboardCheck :size="20" aria-hidden="true" /></span>
          <div>
            <h3>测评反馈</h3>
            <p>Assessment summary / score / feedback</p>
          </div>
        </footer>
      </article>

      <article class="mobbin-card" data-test="student-diagnostics">
        <div class="mobbin-card-stage">
          <span class="mobbin-card-badge">Trace</span>
          <div class="mobbin-phone-preview trace-preview">
            <strong>{{ state.ragTraceId }}</strong>
            <p>RAG stage: {{ displayStatus(state.sseStage) }}</p>
            <ol>
              <li v-for="step in traceSteps.slice(-4)" :key="`${step.actor}-${step.detail}`">
                <span>{{ step.actor }}</span>
                <em :class="['status-pill', step.status.toLowerCase()]">{{ step.status }}</em>
              </li>
            </ol>
          </div>
        </div>
        <footer class="mobbin-card-footer">
          <span class="mobbin-app-icon trace"><GitBranch :size="20" aria-hidden="true" /></span>
          <div>
            <h3>Agent Trace</h3>
            <p>{{ traceSteps.length }} events / citations {{ state.ragSources.length }}</p>
          </div>
        </footer>
      </article>
    </div>
  </section>
</template>
