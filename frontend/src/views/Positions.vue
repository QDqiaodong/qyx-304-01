<template>
  <div class="positions">
    <el-card>
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
        <h3>岗位配置列表</h3>
        <el-button type="primary" @click="openModal">新建岗位</el-button>
      </div>
      <el-table :data="positions" border>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="activityName" label="所属活动" />
        <el-table-column prop="name" label="岗位名称" />
        <el-table-column prop="requiredSkills" label="所需技能" />
        <el-table-column prop="requiredCertificates" label="持证要求" />
        <el-table-column prop="requiredHours" label="服务时长要求" width="120" />
        <el-table-column prop="minCount" label="最少人数" width="80" />
        <el-table-column label="最多人数" width="80">
          <template #default="scope">
            <span>{{ scope.row.maxCount }}</span>
            <el-tag v-if="scope.row.full" type="danger" size="small" style="margin-left: 4px;">已满</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="已占人数" width="110">
          <template #default="scope">
            <span :style="{ color: scope.row.full ? '#ff4d4f' : '#303133', fontWeight: 'bold' }">
              {{ scope.row.occupiedCount ?? 0 }}
            </span>
            <span style="color: #909399;"> / {{ scope.row.maxCount }}</span>
            <div style="font-size: 11px; color: #909399; line-height: 1.4;">
              退回未重提、复核失败不计<br/>证件过期、活动散场立即腾位
            </div>
          </template>
        </el-table-column>
        <el-table-column label="门槛版本" width="130">
          <template #default="scope">
            <el-tag size="small" :type="(scope.row.requirementVersion ?? 1) > 1 ? 'warning' : 'info'">
              v{{ scope.row.requirementVersion ?? 1 }}
            </el-tag>
            <div v-if="(scope.row.requirementVersion ?? 1) > 1" style="font-size: 11px; color: #ff4d4f; margin-top: 2px;">
              要求已更新·在途单已重检
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">
              {{ scope.row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="scope">
            <el-button size="small" @click="editPosition(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deletePosition(scope.row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑岗位' : '新建岗位'" width="600px">
      <el-form :model="formData" label-width="120px">
        <el-form-item label="所属活动" required>
          <el-select v-model="formData.activityId">
            <el-option v-for="activity in activities" :key="activity.id" :label="activity.name" :value="activity.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位名称" required>
          <el-input v-model="formData.name" />
        </el-form-item>
        <el-form-item label="所需技能">
          <el-input v-model="formData.requiredSkills" placeholder="多个技能用逗号分隔" />
        </el-form-item>
        <el-form-item label="持证要求">
          <el-input v-model="formData.requiredCertificates" placeholder="多个证书用逗号分隔" />
        </el-form-item>
        <el-form-item label="服务时长要求">
          <el-input-number v-model="formData.requiredHours" :min="0" />
          <span style="margin-left: 8px;">小时</span>
        </el-form-item>
        <el-alert
          v-if="isEdit && thresholdDirty"
          type="warning"
          :closable="false"
          style="margin-bottom: 16px;"
          title="保存即按新门槛重检：在途报名过不了会停在能力校验失败并让出名额；已批完者保留审批结果，复核失败不计满员。"
        />
        <el-form-item label="最少人数">
          <el-input-number v-model="formData.minCount" :min="1" />
        </el-form-item>
        <el-form-item label="最多人数">
          <el-input-number v-model="formData.maxCount" :min="1" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="formData.status">
            <el-option :label="1" :value="1">启用</el-option>
            <el-option :label="0" :value="0">禁用</el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitFormSafe">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { positionApi, activityApi, type Position, type Activity } from '@/api'

const positions = ref<(Position & { activityName?: string })[]>([])
const activities = ref<Activity[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const thresholdSnapshot = ref({ requiredSkills: '', requiredCertificates: '', requiredHours: 0 })
const norm = (v?: string | null) => (v ?? '').trim()
const thresholdDirty = computed(() =>
  norm(formData.value.requiredSkills) !== norm(thresholdSnapshot.value.requiredSkills) ||
  norm(formData.value.requiredCertificates) !== norm(thresholdSnapshot.value.requiredCertificates) ||
  Number(formData.value.requiredHours || 0) !== Number(thresholdSnapshot.value.requiredHours || 0)
)
const formData = ref<Position>({
  id: 0,
  activityId: 0,
  name: '',
  requiredSkills: '',
  requiredCertificates: '',
  requiredHours: 0,
  minCount: 1,
  maxCount: 10,
  status: 1,
  createdAt: '',
  updatedAt: ''
})

const loadPositions = async () => {
  const [positionsRes, activitiesRes] = await Promise.all([
    positionApi.getAll(),
    activityApi.getAll()
  ])
  activities.value = activitiesRes.data || []
  
  const positionsData = positionsRes.data || []
  positions.value = (positionsData as Position[]).map((pos: Position) => ({
    ...pos,
    activityName: activities.value.find(a => a.id === pos.activityId)?.name || ''
  }))
}

const openModal = () => {
  isEdit.value = false
  formData.value = {
    id: 0,
    activityId: 0,
    name: '',
    requiredSkills: '',
    requiredCertificates: '',
    requiredHours: 0,
    minCount: 1,
    maxCount: 10,
    status: 1,
    createdAt: '',
    updatedAt: ''
  }
  dialogVisible.value = true
}

const editPosition = (position: Position & { activityName?: string }) => {
  isEdit.value = true
  formData.value = { ...position }
  thresholdSnapshot.value = {
    requiredSkills: position.requiredSkills ?? '',
    requiredCertificates: position.requiredCertificates ?? '',
    requiredHours: position.requiredHours ?? 0
  }
  dialogVisible.value = true
}

const deletePosition = async (id: number) => {
  await ElMessageBox.confirm('确认删除该岗位？', '提示', { type: 'warning' })
  await positionApi.delete(id)
  ElMessage.success('已删除')
  loadPositions()
}

const submitForm = async () => {
  if (isEdit.value && thresholdDirty.value) {
    await ElMessageBox.confirm(
      '技能、证书或服务时长门槛已改动，保存后该岗位在途报名将立即按新门槛重检，复核失败的人让出名额、不再算满员。确认保存？',
      '门槛改动将触发重检',
      { type: 'warning', confirmButtonText: '保存并重检', cancelButtonText: '取消' }
    )
  }
  if (isEdit.value) {
    await positionApi.update(formData.value.id, formData.value)
    ElMessage.success(isEdit.value && thresholdDirty.value ? '岗位门槛已更新，在途单已重检' : '岗位已更新')
  } else {
    await positionApi.create(formData.value)
    ElMessage.success('岗位已创建')
  }
  dialogVisible.value = false
  loadPositions()
}

const submitFormSafe = () => {
  submitForm().catch(() => { /* 用户取消确认 */ })
}

onMounted(loadPositions)
</script>

<style scoped>
.positions {
  padding: 20px;
}
</style>