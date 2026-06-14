import { createRouter, createWebHistory, type RouterHistory } from 'vue-router'
import StudentDashboard from './pages/student/StudentDashboard.vue'
import TeacherReviewQueue from './pages/teacher/TeacherReviewQueue.vue'
import AdminOperations from './pages/admin/AdminOperations.vue'
import AdminModelProviders from './pages/admin/AdminModelProviders.vue'

export const routes = [
  {
    path: '/',
    name: 'student',
    component: StudentDashboard,
    meta: { label: '学生学习闭环' },
  },
  {
    path: '/teacher/reviews',
    name: 'teacher',
    component: TeacherReviewQueue,
    meta: { label: '教师审核队列' },
  },
  {
    path: '/admin/operations',
    name: 'admin',
    component: AdminOperations,
    meta: { label: '管理员运维' },
  },
  {
    path: '/admin/model-providers',
    name: 'admin-model-providers',
    component: AdminModelProviders,
    meta: { label: 'Model Providers 配置' },
  },
] as const

export function createAppRouter(history: RouterHistory = createWebHistory()) {
  return createRouter({
    history,
    routes: [...routes],
  })
}
