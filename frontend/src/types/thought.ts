import type { AgentTraceStepResponse, SourceCitationResponse } from './api'

export type ThoughtStatus = 'waiting' | 'running' | 'done' | 'warning' | 'failed'

export interface CurrentThoughtTask {
  title: string
  taskType: string
  model: string
  traceId: string
  startedAt: string
  status: ThoughtStatus
}

export interface ThoughtAgentStep {
  name: string
  status: ThoughtStatus
  duration: string
  summary: string
}

export interface ThoughtRagDocument {
  documentId?: string
  name: string
  pageNum?: number | null
  sectionTitle?: string | null
  score?: number
  excerpt?: string
}

export interface ThoughtRagSources {
  knowledgeBase: string
  chunkCount: number
  documents: ThoughtRagDocument[]
}

export interface ThoughtRuntimeMetrics {
  latency: string
  totalTokens: string
  modelCalls: string
  fallback: string
  safety: string
}

export interface ThoughtPanelData {
  currentTask: CurrentThoughtTask
  agentSteps: ThoughtAgentStep[]
  ragSources: ThoughtRagSources
  metrics: ThoughtRuntimeMetrics
}

export function createDefaultThoughtPanelData(): ThoughtPanelData {
  return {
    currentTask: {
      title: '为“极限概念”生成学习资源',
      taskType: '资源生成',
      model: 'gpt-4o-mini',
      traceId: 'trc_resource_limit_concept_demo',
      startedAt: '2026-06-13 16:40',
      status: 'running',
    },
    agentSteps: [
      {
        name: 'PlannerAgent',
        status: 'done',
        duration: '42 ms',
        summary: '拆解学习目标，确定极限概念的知识前置与资源结构。',
      },
      {
        name: 'TeacherAgent',
        status: 'done',
        duration: '61 ms',
        summary: '生成面向学生的概念解释框架和关键例题提示。',
      },
      {
        name: 'ResourceAgent',
        status: 'running',
        duration: 'running',
        summary: '正在组织讲义、练习、阅读卡片和代码/图示资源。',
      },
      {
        name: 'QuestionAgent',
        status: 'waiting',
        duration: '-',
        summary: '等待资源草稿后生成形成性测评题。',
      },
      {
        name: 'CriticAgent',
        status: 'waiting',
        duration: '-',
        summary: '等待草稿完成后检查引用、准确性和学习画像匹配。',
      },
      {
        name: 'TutorAgent',
        status: 'waiting',
        duration: '-',
        summary: '等待审核通过后准备面向学生的讲解节奏。',
      },
      {
        name: 'SafetyAgent',
        status: 'waiting',
        duration: '-',
        summary: '等待最终草稿后执行安全与无来源风险检查。',
      },
    ],
    ragSources: {
      knowledgeBase: '高等数学课程知识库',
      chunkCount: 8,
      documents: [
        {
          documentId: 'doc_calculus_limit_001',
          name: 'calculus-limit-notes.md',
          pageNum: 12,
          sectionTitle: '极限的直观定义',
          score: 0.94,
          excerpt: '函数值可以无限接近某个稳定数值，但并不要求在该点一定有定义。',
        },
        {
          documentId: 'doc_calculus_limit_002',
          name: 'epsilon-delta-examples.pdf',
          pageNum: 4,
          sectionTitle: 'ε-δ 语言示例',
          score: 0.88,
          excerpt: '用任意小误差约束函数值与目标值之间的距离。',
        },
        {
          documentId: 'doc_calculus_limit_003',
          name: 'limit-practice-set.md',
          pageNum: 2,
          sectionTitle: '常见误区',
          score: 0.81,
          excerpt: '把趋近过程误读成代入计算，是初学极限时最常见的问题。',
        },
      ],
    },
    metrics: {
      latency: '1.28 s',
      totalTokens: '2,846',
      modelCalls: '3',
      fallback: '未触发',
      safety: '执行中',
    },
  }
}

export function normalizeThoughtStatus(status: string | undefined | null): ThoughtStatus {
  const normalized = (status ?? '').trim().toLowerCase()
  if (['done', 'completed', 'complete', 'success', 'succeeded', 'approved'].includes(normalized)) {
    return 'done'
  }
  if (['running', 'active', 'executing', 'streaming', 'pending_critic'].includes(normalized)) {
    return 'running'
  }
  if (['warning', 'blocked', 'needs_review', 'revision_requested'].includes(normalized)) {
    return 'warning'
  }
  if (['failed', 'error', 'cancelled', 'rejected'].includes(normalized)) {
    return 'failed'
  }
  return 'waiting'
}

export function adaptAgentTraceSteps(steps: AgentTraceStepResponse[] | undefined): ThoughtAgentStep[] | undefined {
  if (!steps || steps.length === 0) {
    return undefined
  }
  return steps.map((step) => ({
    name: step.agentName,
    status: normalizeThoughtStatus(step.status),
    duration: `${step.latencyMs} ms`,
    summary: step.summary,
  }))
}

export function adaptRagSources(
  sources: SourceCitationResponse[] | undefined,
  knowledgeBase = '当前课程知识库',
): ThoughtRagSources | undefined {
  if (!sources || sources.length === 0) {
    return undefined
  }
  return {
    knowledgeBase,
    chunkCount: sources.length,
    documents: sources.map((source) => ({
      documentId: source.documentId,
      name: source.documentName,
      pageNum: source.pageNum,
      sectionTitle: source.sectionTitle,
      score: source.score,
      excerpt: source.excerpt,
    })),
  }
}
