<template>
  <div class="activities">
    <el-card>
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
        <h3>志愿活动列表</h3>
        <el-button type="primary" @click="openModal">新建活动</el-button>
      </div>
      <el-table :data="activities" border>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="活动名称" />
        <el-table-column prop="description" label="活动描述" />
        <el-table-column prop="location" label="活动地点" />
        <el-table-column prop="startTime" label="开始时间" />
        <el-table-column prop="endTime" label="结束时间" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">
              {{ scope.row.status === 1 ? '进行中' : '已结束' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="scope">
            <el-button size="small" @click="editActivity(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteActivity(scope.row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑活动' : '新建活动'" width="600px">
      <el-form :model="formData" label-width="100px">
        <el-form-item label="活动名称" required>
          <el-input v-model="formData.name" />
        </el-form-item>
        <el-form-item label="活动描述">
          <el-textarea v-model="formData.description" />
        </el-form-item>
        <el-form-item label="活动地点">
          <el-input v-model="formData.location" />
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-date-picker v-model="formData.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-date-picker v-model="formData.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="formData.status">
            <el-option :label="1" :value="1">进行中</el-option>
            <el-option :label="0" :value="0">已结束</el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { activityApi, type Activity } from '@/api'

const activities = ref<Activity[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const formData = ref<Activity>({
  id: 0,
  name: '',
  description: '',
  startTime: '',
  endTime: '',
  location: '',
  status: 1,
  createdAt: '',
  updatedAt: ''
})

const loadActivities = async () => {
  const res = await activityApi.getAll()
  activities.value = res.data || []
}

const openModal = () => {
  isEdit.value = false
  formData.value = {
    id: 0,
    name: '',
    description: '',
    startTime: '',
    endTime: '',
    location: '',
    status: 1,
    createdAt: '',
    updatedAt: ''
  }
  dialogVisible.value = true
}

const editActivity = (activity: Activity) => {
  isEdit.value = true
  formData.value = { ...activity }
  dialogVisible.value = true
}

const deleteActivity = async (id: number) => {
  await activityApi.delete(id)
  loadActivities()
}

const submitForm = async () => {
  if (isEdit.value) {
    await activityApi.update(formData.value.id, formData.value)
  } else {
    await activityApi.create(formData.value)
  }
  dialogVisible.value = false
  loadActivities()
}

onMounted(loadActivities)
</script>

<style scoped>
.activities {
  padding: 20px;
}
</style>