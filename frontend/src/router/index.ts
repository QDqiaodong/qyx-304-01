import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue')
  },
  {
    path: '/activities',
    name: 'Activities',
    component: () => import('@/views/Activities.vue')
  },
  {
    path: '/positions',
    name: 'Positions',
    component: () => import('@/views/Positions.vue')
  },
  {
    path: '/volunteers',
    name: 'Volunteers',
    component: () => import('@/views/Volunteers.vue')
  },
  {
    path: '/registrations',
    name: 'Registrations',
    component: () => import('@/views/Registrations.vue')
  },
  {
    path: '/approvals',
    name: 'Approvals',
    component: () => import('@/views/Approvals.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router