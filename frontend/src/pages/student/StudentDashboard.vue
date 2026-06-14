<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import WorkspaceComposer from '../../components/workspace/WorkspaceComposer.vue'
import WorkspaceHeader from '../../components/workspace/WorkspaceHeader.vue'
import WorkspaceStream from '../../components/workspace/WorkspaceStream.vue'
import MobbinLearningShowcase from '../../components/learning/MobbinLearningShowcase.vue'
import { submitAnswer } from '../../api/assessment'
import {
  createKnowledgeBase,
  fetchDocumentStatus,
  listKnowledgeBases,
  listKnowledgeBaseDocuments,
  uploadKnowledgeBaseDocument,
} from '../../api/documents'
import { createLearningPath, extractProfile } from '../../api/learning'
import { queryRag, streamChat, streamRagQuery } from '../../api/rag'
import { createResourceGeneration, fetchAgentTrace, fetchResourceGenerationTask } from '../../api/resources'
import type {
  AgentTraceResponse,
  AnswerSubmitResponse,
  DocumentRecord,
  DocumentStatusResponse,
  DocumentUploadResponse,
  GeneratedResource,
  GeneratedResourceStatus,
  GeneratedResourceResponse,
  LearningPathNodeResponse,
  PathNode,
  ProfileExtractResponse,
  RagQueryResponse,
  ResourceGenerationResponse,
  TraceStep,
  WorkbenchState,
} from '../../types/api'

const LEARNER_ID = 'stu_001'
const GOAL_ID = 'goal_java_backend'
const KNOWLEDGE_BASE_NAME = 'Student knowledge base'
const KNOWLEDGE_BASE_DESCRIPTION = 'Student uploaded course materials'
const JOIN_QUESTION_ID = 'student_assessment_question'
const RESOURCE_TYPES = ['LECTURE', 'MIND_MAP', 'EXERCISE', 'READING', 'CODE_LAB']
const REPLAN_NOT_CREATED = 'Not created'
const EMPTY_RESOURCE_TASK_ID = ''

const state = ref<WorkbenchState>({
  knowledgeBase: {
    id: '',
    name: '',
    visibility: '',
    owner: '',
  },
  learnerProfile: {
    learnerId: '',
    major: '',
    goal: '',
    preference: '',
    weakness: '',
    dimensions: [],
  },
  documents: [],
  ragQuestion: '',
  ragTraceId: '',
  sseStage: 'IDLE',
  ragAnswer: '',
  ragSources: [],
  mastery: 0,
  assessmentStatus: '',
  assessmentAnswer: '',
  replanRecordId: REPLAN_NOT_CREATED,
  profilePrompt: '',
  followUpQuestions: [],
  selectedFollowUpQuestion: '',
  goalId: GOAL_ID,
  resourceTaskId: EMPTY_RESOURCE_TASK_ID,
  resourceTaskStatus: '',
  resourceReviewStatus: '',
  resourceProgressPercent: 0,
  resourceSafetyStatus: '',
  agentTaskId: '',
  resourceTraceId: '',
  profileTraceId: '',
  pathTraceId: '',
  pathNodes: [],
  resources: [],
  traceSteps: [],
  loadingAction: '',
  errorMessage: '',
})
const documents = computed(() => state.value.documents)
const resources = computed(() => state.value.resources)
const approvedResources = computed(() => resources.value.filter((resource) => resource.status === 'APPROVED'))
const pendingReviewResources = computed(() =>
  resources.value.filter((resource) => resource.status === 'PENDING_CRITIC'),
)
const revisionResources = computed(() =>
  resources.value.filter((resource) => resource.status === 'REVISION_REQUESTED'),
)
const otherReviewResources = computed(() =>
  resources.value.filter((resource) => resource.status === 'OTHER_REVIEW_STATUS'),
)
const pathNodes = computed(() => state.value.pathNodes)
const traceSteps = computed(() => state.value.traceSteps)
const isLoading = computed(() => state.value.loadingAction !== '')
const selectedDocumentFile = ref<File | null>(null)
const selectedDocumentFileName = computed(() => selectedDocumentFile.value?.name ?? '')
const selectedResourceTypes = ref<string[]>([])

const workflowSteps = computed(() => [
  { id: 'profile', label: '画像', complete: Boolean(state.value.profileTraceId) },
  { id: 'knowledge-bases', label: '知识库', complete: Boolean(state.value.knowledgeBase.id) },
  {
    id: 'documents',
    label: '课程资料',
    complete: documents.value.length > 0 && documents.value.every((document) => document.status !== 'PENDING'),
  },
  { id: 'rag-chat', label: 'RAG 问答', complete: state.value.sseStage === 'DONE' },
  { id: 'citations', label: '引用来源', complete: state.value.ragSources.length > 0 && Boolean(state.value.ragTraceId) },
  { id: 'learning-path', label: '学习路径', complete: pathNodes.value.length > 0 && Boolean(state.value.pathTraceId) },
  { id: 'resources', label: '生成资源', complete: state.value.resourceTaskId !== EMPTY_RESOURCE_TASK_ID && resources.value.length > 0 },
  { id: 'assessment', label: '测评反馈', complete: state.value.replanRecordId !== REPLAN_NOT_CREATED },
])

const indexedDocuments = computed(
  () => documents.value.filter((document) => document.status === 'INDEXED').length,
)

const pendingDocuments = computed(
  () => documents.value.filter((document) => document.status === 'PENDING').length,
)

const averageMastery = computed(() => {
  if (pathNodes.value.length === 0) {
    return state.value.mastery
  }

  const nodeAverage =
    pathNodes.value.reduce((sum, node) => sum + node.mastery, 0) / pathNodes.value.length
  return Math.round((nodeAverage + state.value.mastery) / 2)
})
const localizedErrorMessage = computed(() => displayErrorMessage(state.value.errorMessage))

const usesSensitiveUrlSafeRagTransport = computed(
  () => import.meta.env.PROD || import.meta.env.MODE === 'staging',
)

onMounted(() => {
  void bootstrapWorkbench()
})

async function bootstrapWorkbench() {
  await initializeKnowledgeBase()
}

async function initializeKnowledgeBase() {
  try {
    const knowledgeBases = await listKnowledgeBases()
    const selected =
      knowledgeBases.find((knowledgeBase) => knowledgeBase.name === KNOWLEDGE_BASE_NAME) ??
      knowledgeBases[0]
    if (selected) {
      applyKnowledgeBase(selected)
      await initializeKnowledgeBaseDocuments(selected.id)
      return
    }

    const created = await createKnowledgeBase({
      name: KNOWLEDGE_BASE_NAME,
      description: KNOWLEDGE_BASE_DESCRIPTION,
      visibility: 'PRIVATE',
    })
    applyKnowledgeBase(created)
    await initializeKnowledgeBaseDocuments(created.id)
  } catch (error) {
    captureError('KnowledgeBaseController', error)
  }
}

async function initializeKnowledgeBaseDocuments(kbId: string) {
  try {
    const backendDocuments = await listKnowledgeBaseDocuments(kbId)
    applyDocumentListResponse(backendDocuments)
  } catch (error) {
    captureError('DocumentController', error)
  }
}

function selectFollowUpQuestion(question: string) {
  state.value.profilePrompt = question
  state.value.selectedFollowUpQuestion = question
}

async function refineProfile() {
  const message = state.value.profilePrompt.trim()
  if (!message) {
    state.value.errorMessage = '更新画像前请输入学习者背景。'
    return
  }

  startAction('profile')
  try {
    const profile = await extractProfile({
      learnerId: state.value.learnerProfile.learnerId || LEARNER_ID,
      message,
    })
    applyProfileResponse(profile)
    const path = await createLearningPath({
      learnerId: state.value.learnerProfile.learnerId || LEARNER_ID,
      goalId: state.value.goalId,
    })
    applyLearningPathResponse(path)
  } catch (error) {
    captureError('ProfileAgent', error)
  } finally {
    finishAction('profile')
  }
}

async function uploadDocument() {
  if (state.value.loadingAction === 'document') return

  startAction('document')
  try {
    const file = selectedDocumentFile.value
    if (!file) {
      state.value.errorMessage = '上传前请先选择文档。'
      return
    }
    const activeNode = activePathNode()
    if (!activeNode) {
      state.value.errorMessage = '学习路径为空，请先创建路径再上传文档。'
      return
    }

    const response = await uploadKnowledgeBaseDocument(state.value.knowledgeBase.id, file, {
      courseId: state.value.goalId,
      chapterId: activeNode.id,
    })
    applyDocumentUploadResponse(response, file.name)
    const status = await fetchDocumentStatus(response.documentId)
    applyDocumentStatusResponse(status, response.indexTaskId)
  } catch (error) {
    captureError('DocumentController', error)
  } finally {
    finishAction('document')
  }
}

function selectDocumentFile(event: Event) {
  const input = event.target as HTMLInputElement
  selectedDocumentFile.value = input.files?.[0] ?? null
}

function viewLearningPath() {
  document.getElementById('learning-path-stream-block')?.scrollIntoView({
    behavior: 'smooth',
    block: 'center',
  })
}

async function askRag() {
  if (state.value.loadingAction === 'rag') return
  if (!state.value.ragQuestion.trim()) {
    state.value.errorMessage = '请输入问题'
    return
  }

  startAction('rag')
  state.value.sseStage = 'RETRIEVING'
  state.value.ragAnswer = ''
  const useRestOnlyTransport = usesSensitiveUrlSafeRagTransport.value
  try {
    if (useRestOnlyTransport) {
      await streamRagQueryResponse()
    } else {
      await streamRagResponse()
    }
  } catch (error) {
    if (useRestOnlyTransport) {
      state.value.sseStage = 'ERROR'
      captureError('CourseRagAgent', error)
    } else {
      await fallbackToRagRest(error)
    }
  } finally {
    finishAction('rag')
  }
}

function streamRagResponse(): Promise<void> {
  return new Promise((resolve, reject) => {
    const source = streamChat(`session_${LEARNER_ID}`, state.value.ragQuestion, [
      state.value.knowledgeBase.id,
    ])
    const close = () => source.close()
    const failInvalidPayload = () => {
      close()
      reject(new Error('Invalid SSE event payload'))
    }

    source.addEventListener('status', (event) => {
      const data = parseSsePayload<{ stage?: string }>(event, failInvalidPayload)
      if (!data) return

      if (data.stage) {
        state.value.sseStage = data.stage
      }
    })
    source.addEventListener('token', (event) => {
      const data = parseSsePayload<{ text?: string }>(event, failInvalidPayload)
      if (!data) return

      state.value.ragAnswer += data.text ?? ''
    })
    source.addEventListener('done', (event) => {
      const data = parseSsePayload<{
        traceId: string
        sources: RagQueryResponse['sources']
      }>(event, failInvalidPayload)
      if (!data) return

      applyRagResponse({
        answer: state.value.ragAnswer,
        traceId: data.traceId,
        sources: data.sources ?? [],
      })
      close()
      resolve()
    })
    source.onerror = (event) => {
      close()
      reject(event)
    }
  })
}

async function streamRagQueryResponse() {
  await streamRagQuery(
    {
      kbIds: [state.value.knowledgeBase.id],
      question: state.value.ragQuestion,
      topK: 5,
    },
    {
      onStatus: ({ stage }) => {
        if (stage) {
          state.value.sseStage = stage
        }
      },
      onToken: ({ text }) => {
        state.value.ragAnswer += text ?? ''
      },
      onDone: ({ answer, traceId, sources }) => {
        applyRagResponse({
          answer: answer ?? state.value.ragAnswer,
          traceId: traceId ?? 'trc_stream_missing',
          sources: sources ?? [],
        })
      },
      onError: ({ message }) => {
        throw new Error(message || 'RAG stream failed')
      },
    },
  )
}

function parseSsePayload<T>(event: MessageEvent, onInvalid: () => void): T | null {
  try {
    return JSON.parse(event.data) as T
  } catch {
    onInvalid()
    return null
  }
}

async function fallbackToRagRest(streamError: unknown) {
  try {
    await queryRagRest()
  } catch (restError) {
    state.value.sseStage = 'ERROR'
    captureError('CourseRagAgent', streamError instanceof Error ? streamError : restError)
  }
}

async function queryRagRest() {
  const response = await queryRag({
    kbIds: [state.value.knowledgeBase.id],
    question: state.value.ragQuestion,
    topK: 5,
  })
  applyRagResponse(response)
}

async function generateResources() {
  startAction('resources')
  if (selectedResourceTypes.value.length === 0) {
    state.value.errorMessage = '请先选择资源类型'
    finishAction('resources')
    return
  }
  const activeNode = activePathNode()
  if (!activeNode) {
    state.value.errorMessage = '学习路径为空，请先创建路径再生成资源。'
    finishAction('resources')
    return
  }

  try {
    const response = await createResourceGeneration({
      learnerId: state.value.learnerProfile.learnerId || LEARNER_ID,
      goalId: state.value.goalId,
      pathNodeId: activeNode.id,
      resourceTypes: selectedResourceTypes.value,
    })
    applyResourceGenerationResponse(response)

    const trace = await fetchAgentTrace(response.agentTaskId)
    applyAgentTrace(trace)
  } catch (error) {
    captureError('ResourceAgent', error)
  } finally {
    finishAction('resources')
  }
}

async function refreshResourceStatus() {
  if (state.value.resourceTaskId === EMPTY_RESOURCE_TASK_ID) return

  startAction('resource-status')
  try {
    const response = await fetchResourceGenerationTask(state.value.resourceTaskId)
    applyResourceGenerationResponse(response)
    appendTrace({
      actor: 'ReviewGovernance',
      status: 'DONE',
      detail: `Refreshed generation task ${response.taskId}; review status ${response.reviewStatus}.`,
      latencyMs: 88,
    })
  } catch (error) {
    captureError('ReviewGovernance', error)
  } finally {
    finishAction('resource-status')
  }
}

async function submitAssessment() {
  if (!state.value.assessmentAnswer.trim()) {
    state.value.errorMessage = '请填写作答内容'
    return
  }

  startAction('assessment')
  try {
    const response = await submitAnswer({
      learnerId: state.value.learnerProfile.learnerId || LEARNER_ID,
      questionId: JOIN_QUESTION_ID,
      answer: state.value.assessmentAnswer,
    })
    applyAssessmentResponse(response)
  } catch (error) {
    captureError('AssessmentAgent', error)
  } finally {
    finishAction('assessment')
  }
}

function appendTrace(step: TraceStep) {
  state.value.traceSteps = [...state.value.traceSteps, step]
}

function applyDocumentUploadResponse(response: DocumentUploadResponse, fileName: string) {
  const document: DocumentRecord = {
    id: response.documentId,
    name: fileName,
    type: 'Markdown',
    status: normalizeDocumentStatus(response.status),
    indexTaskId: response.indexTaskId,
    chunks: 0,
    updatedAt: new Date().toISOString().slice(0, 16).replace('T', ' '),
  }
  state.value.documents = [
    ...state.value.documents.filter((existing) => existing.id !== response.documentId),
    document,
  ]
  appendTrace({
    actor: 'DocumentController',
    status: 'RUNNING',
    detail: `Uploaded ${response.documentId} and created index task ${response.indexTaskId}.`,
    latencyMs: 96,
  })
}

function applyDocumentStatusResponse(response: DocumentStatusResponse, indexTaskId: string) {
  const document: DocumentRecord = {
    id: response.documentId,
    name: response.name,
    type: inferDocumentType(response.name),
    status: normalizeBackendDocumentStatus(response.indexStatus),
    indexTaskId,
    chunks: 0,
    updatedAt: new Date().toISOString().slice(0, 16).replace('T', ' '),
  }
  state.value.documents = [
    ...state.value.documents.filter((existing) => existing.id !== response.documentId),
    document,
  ]
  appendTrace({
    actor: 'DocumentController',
    status: document.status === 'INDEXED' ? 'DONE' : 'RUNNING',
    detail: `Document ${response.documentId} parse ${response.parseStatus}, index ${response.indexStatus}.`,
    latencyMs: 64,
  })
}

function applyDocumentListResponse(response: DocumentStatusResponse[]) {
  state.value.documents = response.map((document) => toDocumentRecord(document))
}

function toDocumentRecord(response: DocumentStatusResponse): DocumentRecord {
  return {
    id: response.documentId,
    name: response.name,
    type: inferDocumentType(response.name),
    status: normalizeBackendDocumentStatus(response.indexStatus),
    indexTaskId: `v${response.version}`,
    chunks: 0,
    updatedAt: 'Backend document',
  }
}

function applyKnowledgeBase(response: {
  id: string
  name: string
  visibility: string
  ownerUserId: string
}) {
  state.value.knowledgeBase = {
    id: response.id,
    name: response.name,
    visibility: response.visibility,
    owner: response.ownerUserId,
  }
}

function applyProfileResponse(response: ProfileExtractResponse) {
  const draft = response.profileDraft
  state.value.profileTraceId = response.traceId
  state.value.followUpQuestions = response.followUpQuestions
  if (!response.followUpQuestions.includes(state.value.selectedFollowUpQuestion)) {
    state.value.selectedFollowUpQuestion = ''
  }
  state.value.learnerProfile = {
    learnerId: draft.learnerId,
    major: state.value.learnerProfile.major,
    goal: draft.target,
    preference: draft.preferences.join(', '),
    weakness: draft.weakPoints.join(', '),
    dimensions: draft.dimensions.map((dimension) => ({
      name: dimension.name,
      value: dimension.value,
      confidence: dimension.confidence,
      evidence: dimension.evidence,
    })),
  }
  upsertTrace({
    actor: 'ProfileAgent',
    status: 'DONE',
    detail: `${response.reasonSummary} Trace ${response.traceId}.`,
    latencyMs: 180,
  })
}

function applyResourceGenerationResponse(response: ResourceGenerationResponse) {
  state.value.resourceTaskId = response.taskId
  state.value.resourceTaskStatus = response.status
  state.value.resourceReviewStatus = response.reviewStatus
  state.value.resourceProgressPercent = response.progressPercent
  state.value.resourceSafetyStatus = response.safetyStatus
  state.value.agentTaskId = response.agentTaskId
  state.value.resourceTraceId = response.traceId
  state.value.resources = (response.resources ?? []).map(toGeneratedResource)
}

function applyLearningPathResponse(response: {
  goalId: string
  nodes: LearningPathNodeResponse[]
  reasonSummary: string
  traceId: string
}) {
  state.value.goalId = response.goalId
  state.value.pathTraceId = response.traceId
  state.value.pathNodes = response.nodes.map(toPathNode)
  upsertTrace({
    actor: 'PathPlannerAgent',
    status: 'DONE',
    detail: `${response.reasonSummary} Trace ${response.traceId}.`,
    latencyMs: 240,
  })
}

function applyRagResponse(response: RagQueryResponse) {
  state.value.sseStage = 'DONE'
  state.value.ragTraceId = response.traceId
  state.value.ragAnswer = response.answer
  state.value.ragSources = (response.sources ?? []).map((source) => ({
    documentName: source.documentName,
    pageNum: source.pageNum,
    sectionTitle: source.sectionTitle,
    excerpt: source.excerpt,
    score: source.score,
  }))
  state.value.traceSteps = state.value.traceSteps.map((step) =>
    step.actor === 'CourseRagAgent'
      ? {
          ...step,
          status: 'DONE',
          detail: 'Backend RAG query completed with permission-filtered citations.',
          latencyMs: 1200,
        }
      : step,
  )
}

function upsertTrace(step: TraceStep) {
  const withoutActor = state.value.traceSteps.filter((existing) => existing.actor !== step.actor)
  state.value.traceSteps = [...withoutActor, step]
}

function applyAgentTrace(response: AgentTraceResponse) {
  const traceStepsFromBackend = response.steps.map((step) => ({
    actor: step.agentName,
    status: normalizeTraceStatus(step.status),
    detail: step.summary,
    latencyMs: step.latencyMs,
  }))
  state.value.traceSteps = [
    ...state.value.traceSteps.filter(
      (step) => !traceStepsFromBackend.some((backendStep) => backendStep.actor === step.actor),
    ),
    ...traceStepsFromBackend,
  ]
}

function applyAssessmentResponse(response: AnswerSubmitResponse) {
  const update = response.masteryUpdates[0]
  const nextMastery = update ? asPercent(update.afterMastery) : asPercent(response.score)
  state.value.mastery = nextMastery
  state.value.assessmentStatus = response.wrongCauseAnalysis
  state.value.replanRecordId = response.replanRecordId
  if (update) {
    state.value.pathNodes = state.value.pathNodes.map((node) =>
      node.id === update.knowledgePointId
        ? {
            ...node,
            mastery: nextMastery,
            reason: update.reasonSummary,
          }
        : node,
    )
  }
  appendTrace({
    actor: 'AssessmentAgent',
    status: 'DONE',
    detail: `Graded answer ${response.answerId}; remediation strategy ${response.resourcePushStrategy}.`,
    latencyMs: 320,
  })
}

function toPathNode(node: LearningPathNodeResponse): PathNode {
  return {
    id: node.nodeId,
    title: node.title,
    status: normalizePathStatus(node.status),
    reason: node.reasonSummary,
    mastery: asPercent(node.mastery),
  }
}

function toGeneratedResource(resource: GeneratedResourceResponse): GeneratedResource {
  return {
    resourceId: resource.resourceId,
    type: resource.type,
    modality: resource.modality,
    title: resource.title,
    status: normalizeResourceStatus(resource.reviewStatus),
    reviewStatus: resource.reviewStatus,
    reviewer: 'CriticAgent',
    citationSummary: resource.citationSummary,
    markdownContent: resource.markdownContent,
    safetyStatus: resource.safetyStatus,
  }
}

function normalizeResourceStatus(status: string): GeneratedResourceStatus {
  if (status === 'PENDING_CRITIC' || status === 'APPROVED' || status === 'REVISION_REQUESTED') {
    return status
  }
  return 'OTHER_REVIEW_STATUS'
}

function normalizePathStatus(status: string): PathNode['status'] {
  if (status === 'READY' || status === 'ACTIVE' || status === 'LOCKED' || status === 'DONE') {
    return status
  }
  return 'LOCKED'
}

function normalizeDocumentStatus(status: DocumentUploadResponse['status']): DocumentRecord['status'] {
  if (status === 'COMPLETED') return 'INDEXED'
  return status === 'FAILED' ? 'FAILED' : 'PENDING'
}

function normalizeBackendDocumentStatus(
  status: DocumentStatusResponse['indexStatus'],
): DocumentRecord['status'] {
  if (status === 'INDEXED') return 'INDEXED'
  if (status === 'FAILED') return 'FAILED'
  return status === 'READY' ? 'READY' : 'PENDING'
}

function inferDocumentType(name: string): string {
  const extension = name.split('.').pop()?.toLowerCase()
  if (extension === 'pdf') return 'PDF'
  if (extension === 'md' || extension === 'markdown') return 'Markdown'
  return 'Document'
}

function activePathNode(): PathNode | null {
  return pathNodes.value.find((node) => node.status === 'ACTIVE') ?? pathNodes.value[0]
}

function startAction(action: string) {
  state.value.loadingAction = action
  state.value.errorMessage = ''
}

function finishAction(action: string) {
  if (state.value.loadingAction === action) {
    state.value.loadingAction = ''
  }
}

function captureError(actor: string, error: unknown) {
  const message = error instanceof Error ? error.message : 'Request failed'
  state.value.errorMessage = message
  appendTrace({
    actor,
    status: 'ERROR',
    detail: message,
    latencyMs: 0,
  })
}

function displayErrorMessage(message: string) {
  const errorLabels: Record<string, string> = {
    'Failed to fetch': '请求失败',
    'Request failed': '请求失败',
    'Invalid SSE event payload': 'SSE 事件数据无效',
    'RAG stream failed': 'RAG 流式请求失败',
  }
  return errorLabels[message] ?? message
}

function displayStatus(status: string) {
  const statusLabels: Record<string, string> = {
    IDLE: '空闲',
    RETRIEVING: '检索中',
    DONE: '完成',
    ERROR: '错误',
    READY: '就绪',
    ACTIVE: '当前',
    LOCKED: '锁定',
    DRAFT: '草稿',
    PENDING: '待处理',
    PENDING_CRITIC: '待审核',
    APPROVED: '已通过',
    REVISION_REQUESTED: '需修改',
    OTHER_REVIEW_STATUS: '其他状态',
    COMPLETED: '已完成',
    PASS: '通过',
    BLOCKED: '已拦截',
  }
  return statusLabels[status] ?? status
}

function displayReplanRecordId(replanRecordId: string) {
  return replanRecordId === REPLAN_NOT_CREATED ? '未创建' : replanRecordId
}

function normalizeTraceStatus(status: string): TraceStep['status'] {
  if (status === 'DONE' || status === 'RUNNING' || status === 'PENDING' || status === 'WAITING') {
    return status
  }
  if (status === 'COMPLETED') return 'DONE'
  return 'ERROR'
}

function asPercent(value: number): number {
  return Math.round(value <= 1 ? value * 100 : value)
}
</script>

<template>
  <section class="workspace student-ai-page" aria-label="Learning workbench">
    <div class="student-ai-workspace">
      <WorkspaceHeader
        :title="state.learnerProfile.goal"
        :course-name="state.knowledgeBase.name"
        :topic-name="state.learnerProfile.weakness"
        :stage="state.sseStage"
        :mastery="averageMastery"
        :indexed-documents="indexedDocuments"
        :pending-documents="pendingDocuments"
        :workflow-steps="workflowSteps"
      />
      <WorkspaceStream
        v-model:assessment-answer="state.assessmentAnswer"
        :question="state.ragQuestion"
        :profile-prompt="state.profilePrompt"
        :answer="state.ragAnswer"
        :stage="state.sseStage"
        :trace-id="state.ragTraceId"
        :sources="state.ragSources"
        :path-nodes="pathNodes"
        :resources="resources"
        :resource-task-status="state.resourceTaskStatus"
        :resource-review-status="state.resourceReviewStatus"
        :resource-progress-percent="state.resourceProgressPercent"
        :resource-safety-status="state.resourceSafetyStatus"
        :mastery="state.mastery"
        :assessment-status="state.assessmentStatus"
        :replan-record-id="state.replanRecordId"
        :error-message="localizedErrorMessage"
      />
      <WorkspaceComposer
        v-model:question="state.ragQuestion"
        v-model:selected-resource-types="selectedResourceTypes"
        :selected-file-name="selectedDocumentFileName"
        :resource-types="RESOURCE_TYPES"
        :is-loading="isLoading"
        :loading-action="state.loadingAction"
        @select-file="selectDocumentFile"
        @upload="uploadDocument"
        @generate="generateResources"
        @assess="submitAssessment"
        @view-path="viewLearningPath"
        @send="askRag"
      />
    </div>

    <MobbinLearningShowcase
      v-model:selected-resource-types="selectedResourceTypes"
      :state="state"
      :documents="documents"
      :path-nodes="pathNodes"
      :resources="resources"
      :trace-steps="traceSteps"
      :selected-document-file-name="selectedDocumentFileName"
      :resource-types="RESOURCE_TYPES"
      :indexed-documents="indexedDocuments"
      :pending-documents="pendingDocuments"
      :average-mastery="averageMastery"
      :approved-resources="approvedResources"
      :pending-review-resources="pendingReviewResources"
      :revision-resources="revisionResources"
      :other-review-resources="otherReviewResources"
      :localized-error-message="localizedErrorMessage"
      :is-loading="isLoading"
      :display-status="displayStatus"
      :display-replan-record-id="displayReplanRecordId"
      @select-file="selectDocumentFile"
      @upload="uploadDocument"
      @generate="generateResources"
      @refresh-resource-status="refreshResourceStatus"
      @assess="submitAssessment"
      @refine-profile="refineProfile"
      @select-follow-up-question="selectFollowUpQuestion"
    />

  </section>
</template>
<style scoped>
.student-ai-page {
  display: block;
  align-content: stretch;
  gap: 0;
  min-height: 100svh;
  padding: 0;
  overflow: visible;
  background:
    linear-gradient(180deg, rgba(248, 250, 252, 0.82), rgba(241, 245, 249, 0.92)),
    #f6f7fb;
}

.student-ai-workspace {
  position: relative;
  min-height: 100svh;
}

.student-legacy-panels {
  display: none;
}
</style>
