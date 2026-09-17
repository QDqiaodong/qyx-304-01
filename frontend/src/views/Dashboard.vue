<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-icon activities">
            <el-icon><Calendar /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.activities }}</div>
            <div class="stat-label">志愿活动</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-icon volunteers">
            <el-icon><User /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.volunteers }}</div>
            <div class="stat-label">志愿者</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-icon registrations">
            <el-icon><Document /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.registrations }}</div>
            <div class="stat-label">报名总数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-icon pending">
            <el-icon><Clock /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.pendingApprovals }}</div>
            <div class="stat-label">待审批</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="12">
        <el-card title="最近报名">
          <el-table :data="recentRegistrations" border>
            <el-table-column prop="volunteerName" label="志愿者" />
            <el-table-column prop="activityName" label="活动" />
            <el-table-column prop="positionName" label="岗位" />
            <el-table-column prop="statusDesc" label="状态" />
            <el-table-column prop="createdAt" label="报名时间" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card title="待审批报名单">
          <el-table :data="pendingRegistrations" border>
            <el-table-column prop="volunteerName" label="志愿者" />
            <el-table-column prop="activityName" label="活动" />
            <el-table-column prop="positionName" label="岗位" />
            <el-table-column prop="currentApprovalNodeDesc" label="当前节点" />
            <el-table-column prop="createdAt" label="时间" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Calendar, User, Document, Clock } from '@element-plus/icons-vue'
import { activityApi, volunteerApi, registrationApi, type RegistrationDetail } from '@/api'

const statistics = ref({
  activities: 0,
  volunteers: 0,
  registrations: 0,
  pendingApprovals: 0
})

const recentRegistrations = ref<RegistrationDetail[]>([])
const pendingRegistrations = ref<RegistrationDetail[]>([])

const loadData = async () => {
  const [activitiesRes, volunteersRes, registrationsRes, pendingRes] = await Promise.all([
    activityApi.getAll(),
    volunteerApi.getAll(),
    registrationApi.getAll(),
    registrationApi.getPending(1)
  ])

  const activities = activitiesRes.data || []
  const volunteers = volunteersRes.data || []
  const registrations = registrationsRes.data || []
  const pending = pendingRes.data || []

  statistics.value = {
    activities: activities.length || 0,
    volunteers: volunteers.length || 0,
    registrations: registrations.length || 0,
    pendingApprovals: pending.length || 0
  }

  recentRegistrations.value = registrations.slice(0, 5)
  pendingRegistrations.value = pending.slice(0, 5)
}

onMounted(loadData)
</script>

<style scoped>
.dashboard {
  padding: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  padding: 20px;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  margin-right: 20px;
}

.stat-icon.activities {
  background-color: #e6f7ff;
  color: #1890ff;
}

.stat-icon.volunteers {
  background-color: #f6ffed;
  color: #52c41a;
}

.stat-icon.registrations {
  background-color: #fff7e6;
  color: #fa8c16;
}

.stat-icon.pending {
  background-color: #fff0f6;
  color: #eb2f96;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}
</style>