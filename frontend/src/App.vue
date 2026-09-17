<template>
  <el-container style="height: 100vh;">
    <el-aside width="200px" style="background-color: #304156;">
      <div class="logo" style="color: white; font-size: 18px; text-align: center; padding: 20px 0; border-bottom: 1px solid #405467;">
        志愿审批系统
      </div>
      <el-menu :default-active="activeMenu" class="el-menu-vertical-demo" style="background-color: #304156; border-right: none;" text-color="#bfcbd9" active-text-color="#409EFF" @select="handleMenuSelect">
        <el-menu-item index="/dashboard">
          <el-icon><Monitor /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/activities">
          <el-icon><Calendar /></el-icon>
          <span>志愿活动</span>
        </el-menu-item>
        <el-menu-item index="/positions">
          <el-icon><Position /></el-icon>
          <span>岗位配置</span>
        </el-menu-item>
        <el-menu-item index="/volunteers">
          <el-icon><User /></el-icon>
          <span>志愿者管理</span>
        </el-menu-item>
        <el-menu-item index="/registrations">
          <el-icon><Document /></el-icon>
          <span>报名管理</span>
        </el-menu-item>
        <el-menu-item index="/approvals">
          <el-icon><CircleCheck /></el-icon>
          <span>审批管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="background-color: #fff; border-bottom: 1px solid #e6e6e6; display: flex; justify-content: space-between; align-items: center;">
        <h2 style="margin: 0;">{{ pageTitle }}</h2>
        <div style="color: #666;">公益志愿活动人员能力匹配流程审批系统</div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Monitor, Calendar, Position, User, Document, CircleCheck } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()

const activeMenu = computed(() => route.path)

const pageTitles: Record<string, string> = {
  '/dashboard': '仪表盘',
  '/activities': '志愿活动',
  '/positions': '岗位配置',
  '/volunteers': '志愿者管理',
  '/registrations': '报名管理',
  '/approvals': '审批管理'
}

const pageTitle = computed(() => pageTitles[route.path] || '仪表盘')

const handleMenuSelect = (index: string) => {
  router.push(index)
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  height: 100%;
}

.el-menu-vertical-demo:not(.el-menu--collapse) {
  width: 200px;
}

.el-menu-item {
  margin: 0 10px !important;
  border-radius: 4px !important;
}

.el-menu-item:hover {
  background-color: #405467 !important;
}

.el-menu-item.is-active {
  background-color: #409EFF !important;
}
</style>