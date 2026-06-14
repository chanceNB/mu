<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  BookOpenCheck,
  BrainCircuit,
  Clock3,
  GraduationCap,
  LibraryBig,
  Plus,
  Search,
  Settings,
  ShieldCheck,
  SlidersHorizontal,
  UserRound,
  Wrench,
} from 'lucide-vue-next'

type SidebarNavItem = {
  label: string
  detail: string
  to?: string
  routeNames: string[]
  icon: unknown
  disabled?: boolean
  test?: string
}

type HistoryItem = {
  title: string
  type: string
  time: string
  status: 'active' | 'done' | 'draft'
}

type HistoryGroup = {
  label: string
  items: HistoryItem[]
}

const route = useRoute()
const historySearch = ref('')

const navigation: SidebarNavItem[] = [
  {
    label: '学习工作台',
    detail: 'AI 学习闭环',
    to: '/',
    routeNames: ['student'],
    icon: BookOpenCheck,
    test: 'student-view',
  },
  {
    label: '教师审核',
    detail: '资源发布闸口',
    to: '/teacher/reviews',
    routeNames: ['teacher'],
    icon: ShieldCheck,
    test: 'teacher-view',
  },
  {
    label: '运维面板',
    detail: '健康与指标',
    to: '/admin/operations',
    routeNames: ['admin'],
    icon: Wrench,
    test: 'admin-view',
  },
  {
    label: '模型供应商',
    detail: '模型路由配置',
    to: '/admin/model-providers',
    routeNames: ['admin-model-providers'],
    icon: SlidersHorizontal,
    test: 'admin-model-providers-view',
  },
  {
    label: '知识库',
    detail: '待接入路由',
    routeNames: [],
    icon: LibraryBig,
    disabled: true,
  },
  {
    label: '设置',
    detail: '待接入路由',
    routeNames: [],
    icon: Settings,
    disabled: true,
  },
]

const historyGroups: HistoryGroup[] = []

const normalizedSearch = computed(() => historySearch.value.trim().toLowerCase())

const filteredHistoryGroups = computed(() => {
  if (!normalizedSearch.value) {
    return historyGroups
  }

  return historyGroups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) =>
        `${item.title} ${item.type} ${item.time} ${item.status}`
          .toLowerCase()
          .includes(normalizedSearch.value),
      ),
    }))
    .filter((group) => group.items.length > 0)
})

const routeContext = computed(() => {
  if (route.name === 'teacher') {
    return '审核工作台'
  }

  if (route.name === 'admin' || route.name === 'admin-model-providers') {
    return '系统健康总览'
  }

  return '主动学习闭环'
})

function isActive(item: SidebarNavItem) {
  return item.routeNames.includes(String(route.name))
}
</script>

<template>
  <aside class="sidebar left-sidebar" aria-label="AI Learning OS navigation">
    <div class="sidebar-brand">
      <div class="sidebar-brand-mark">
        <GraduationCap :size="22" aria-hidden="true" />
      </div>
      <div>
        <p>AI Learning OS</p>
        <h1>个性化学习智能体平台</h1>
      </div>
    </div>
    <p class="visually-hidden" data-test="shell-context">{{ routeContext }}</p>

    <button class="new-learning-button" type="button" data-test="new-learning-task">
      <Plus :size="17" aria-hidden="true" />
      <span>新建学习任务</span>
    </button>

    <nav class="sidebar-section" aria-label="功能区块">
      <h2>功能区块</h2>
      <div class="sidebar-nav-list">
        <RouterLink
          v-for="item in navigation.filter((navItem) => !navItem.disabled)"
          :key="item.label"
          :class="['sidebar-nav-item', { active: isActive(item) }]"
          :to="item.to ?? '/'"
          :data-test="item.test"
        >
          <component :is="item.icon" :size="17" aria-hidden="true" />
          <span>
            <strong>{{ item.label }}</strong>
            <small>{{ item.detail }}</small>
          </span>
        </RouterLink>
        <button
          v-for="item in navigation.filter((navItem) => navItem.disabled)"
          :key="item.label"
          class="sidebar-nav-item disabled"
          type="button"
          disabled
        >
          <component :is="item.icon" :size="17" aria-hidden="true" />
          <span>
            <strong>{{ item.label }}</strong>
            <small>{{ item.detail }}</small>
          </span>
        </button>
      </div>
    </nav>

    <section class="sidebar-history" aria-label="历史记录">
      <div class="sidebar-section-heading">
        <h2>历史记录</h2>
        <Clock3 :size="15" aria-hidden="true" />
      </div>
      <label class="history-search">
        <Search :size="15" aria-hidden="true" />
        <input v-model="historySearch" type="search" placeholder="搜索历史..." />
      </label>

      <div class="history-groups">
        <section v-for="group in filteredHistoryGroups" :key="group.label" class="history-group">
          <h3>{{ group.label }}</h3>
          <button v-for="item in group.items" :key="`${group.label}-${item.title}`" class="history-item" type="button">
            <span class="history-main">
              <strong>{{ item.title }}</strong>
              <small>{{ item.type }} · {{ item.time }}</small>
            </span>
            <em :class="['history-status', item.status]">{{ item.status }}</em>
          </button>
        </section>
        <p v-if="filteredHistoryGroups.length === 0" class="history-empty">没有匹配的学习记录</p>
      </div>
    </section>

    <div class="sidebar-user">
      <div class="avatar-token">
        <UserRound :size="18" aria-hidden="true" />
      </div>
      <div>
        <strong>当前用户</strong>
        <span>个人账户</span>
      </div>
      <BrainCircuit :size="16" aria-hidden="true" />
    </div>
  </aside>
</template>

<style scoped>
.left-sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: var(--shell-sidebar-width);
  min-height: 100vh;
  padding: 18px 14px;
  color: #202123;
  background: #f7f7f8;
  border-right: 1px solid #e5e7eb;
}

.sidebar-brand {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  padding: 6px 6px 10px;
}

.sidebar-brand-mark {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  color: #ffffff;
  background: linear-gradient(135deg, #4f46e5, #8b5cf6);
  border-radius: 8px;
}

.sidebar-brand p,
.sidebar-brand h1 {
  overflow-wrap: anywhere;
}

.sidebar-brand p {
  color: #343541;
  font-size: 15px;
  font-weight: 800;
  line-height: 1.15;
}

.sidebar-brand h1 {
  margin-top: 3px;
  color: #6b7280;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.3;
}

.new-learning-button,
.sidebar-nav-item,
.history-item {
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
  border: 0;
  border-radius: 8px;
  cursor: pointer;
}

.new-learning-button {
  justify-content: center;
  gap: 8px;
  min-height: 42px;
  padding: 10px 12px;
  color: #ffffff;
  font-size: 14px;
  font-weight: 800;
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  box-shadow: 0 10px 22px rgba(79, 70, 229, 0.22);
}

.sidebar-section,
.sidebar-history {
  display: grid;
  gap: 8px;
}

.sidebar-section h2,
.sidebar-section-heading h2,
.history-group h3 {
  color: #6b7280;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0;
}

.sidebar-section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 6px;
}

.sidebar-nav-list,
.history-groups,
.history-group {
  display: grid;
  gap: 4px;
}

.sidebar-nav-item {
  gap: 10px;
  min-height: 44px;
  padding: 8px 10px;
  color: #343541;
  text-decoration: none;
  background: transparent;
  transition:
    background-color 160ms ease,
    color 160ms ease;
}

.sidebar-nav-item:hover {
  background: #ececf1;
}

.sidebar-nav-item.active {
  color: #3730a3;
  background: #ecebff;
}

.sidebar-nav-item.disabled {
  color: #9ca3af;
  cursor: not-allowed;
}

.sidebar-nav-item svg {
  flex: 0 0 auto;
}

.sidebar-nav-item span {
  display: grid;
  gap: 1px;
  min-width: 0;
  text-align: left;
}

.sidebar-nav-item strong,
.history-item strong {
  overflow: hidden;
  font-size: 14px;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-nav-item small,
.history-item small {
  overflow: hidden;
  color: #6b7280;
  font-size: 12px;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-history {
  min-height: 0;
}

.history-search {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 7px;
  align-items: center;
  min-height: 36px;
  padding: 0 10px;
  color: #6b7280;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.history-search input {
  min-width: 0;
  color: #202123;
  font: inherit;
  font-size: 13px;
  background: transparent;
  border: 0;
  outline: 0;
}

.history-search input::placeholder {
  color: #9ca3af;
}

.history-groups {
  max-height: min(42vh, 430px);
  padding-right: 2px;
  overflow-y: auto;
}

.history-group {
  padding-top: 4px;
}

.history-group h3 {
  padding: 8px 6px 3px;
}

.history-item {
  justify-content: space-between;
  gap: 8px;
  min-height: 46px;
  padding: 8px 9px;
  color: #343541;
  text-align: left;
  background: transparent;
}

.history-item:hover {
  background: #ececf1;
}

.history-main {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.history-status {
  flex: 0 0 auto;
  padding: 2px 6px;
  font-size: 11px;
  font-style: normal;
  font-weight: 800;
  line-height: 1.2;
  border-radius: 999px;
}

.history-status.active {
  color: #3730a3;
  background: #e0e7ff;
}

.history-status.done {
  color: #047857;
  background: #d1fae5;
}

.history-status.draft {
  color: #92400e;
  background: #fef3c7;
}

.history-empty {
  padding: 10px 8px;
  color: #6b7280;
  font-size: 13px;
}

.sidebar-user {
  grid-template-columns: 38px minmax(0, 1fr) auto;
  margin-top: auto;
  background: #ffffff;
}

.sidebar-user > svg {
  color: #8b5cf6;
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

@media (max-width: 1220px) {
  .left-sidebar {
    width: 100%;
    min-height: auto;
  }

  .sidebar-history {
    grid-column: 1 / -1;
  }

  .history-groups {
    max-height: 260px;
  }
}

@media (max-width: 760px) {
  .left-sidebar {
    max-width: 100vw;
    padding: 16px;
  }
}
</style>
