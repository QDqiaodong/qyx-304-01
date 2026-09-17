<template>
  <div class="registrations">
    <el-card>
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
        <h3>报名管理</h3>
        <el-button type="primary" @click="openApplyModal">发起报名</el-button>
      </div>
      <div style="display: flex; gap: 20px; margin-bottom: 20px;">
        <el-select v-model="filterStatus" placeholder="全部状态">
          <el-option label="全部" :value="-1" />
          <el-option label="待审批" :value="0" />
          <el-option label="已通过" :value="1" />
          <el-option label="已驳回" :value="2" />
          <el-option label="退回修改" :value="3" />
          <el-option label="审批完成" :value="4" />
          <el-option label="能力校验失败" :value="5" />
        </el-select>
        <el-select v-model="filterCheckPass" placeholder="能力校验">
          <el-option :label="-1" :value="-1">全部</el-option>
          <el-option :label="1" :value="1">通过</el-option>
          <el-option :label="0" :value="0">未通过</el-option>
        </el-select>
        <el-button @click="loadRegistrations">查询</el-button>
      </div>
      <el-table :data="registrations" border>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="volunteerName" label="志愿者" />
        <el-table-column prop="volunteerPhone" label="联系方式" />
        <el-table-column prop="activityName" label="活动" />
        <el-table-column prop="positionName" label="岗位" />
        <el-table-column label="排班闸门" width="150">
          <template #default="scope">
            <el-tag v-if="scope.row.activityOngoing === false" type="danger" size="small">
              活动已散场
            </el-tag>
            <el-tag
              v-for="cert in (scope.row.expiredCertificates || [])"
              :key="cert"
              type="danger"
              size="small"
              style="margin-top: 2px;"
            >
              {{ cert }}已过期
            </el-tag>
            <el-tag
              v-if="scope.row.activityOngoing !== false && !(scope.row.expiredCertificates || []).length"
              type="success"
              size="small"
            >
              闸门正常
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="能力校验" width="120">
          <template #default="scope">
            <el-tag :type="scope.row.effectivePass === 1 ? 'success' : 'danger'">
              {{ scope.row.effectivePassDesc }}
            </el-tag>
            <div v-if="scope.row.recheckPass !== null && scope.row.recheckPass !== undefined"
                 style="font-size: 11px; margin-top: 2px;"
                 :style="{ color: scope.row.recheckPass === 1 ? '#52c41a' : '#ff4d4f' }">
              新门槛{{ scope.row.recheckPass === 1 ? '复核通过' : '复核失败' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="currentApprovalNodeDesc" label="当前节点" width="120" />
        <el-table-column prop="statusDesc" label="状态" width="110">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)">
              {{ scope.row.statusDesc }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="报名时间" />
        <el-table-column label="操作" width="200">
          <template #default="scope">
            <el-button size="small" @click="viewDetail(scope.row)">详情</el-button>
            <el-button
              v-if="scope.row.status === 3"
              size="small"
              type="warning"
              @click="openResubmitModal(scope.row)"
            >重新送审</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="applyModalVisible" title="发起报名" width="600px">
      <el-form :model="applyForm" label-width="100px">
        <el-form-item label="选择志愿者" required>
          <el-select v-model="applyForm.volunteerId" placeholder="请选择志愿者" @change="onVolunteerChange">
            <el-option v-for="v in volunteers" :key="v.id" :label="v.name" :value="v.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="选择活动" required>
          <el-select v-model="applyForm.activityId" placeholder="请选择活动" @change="onActivityChange">
            <el-option
              v-for="a in activities"
              :key="a.id"
              :label="a.name + (isActivityEnded(a) ? '（已散场）' : '')"
              :value="a.id"
            />
          </el-select>
          <el-alert
            v-if="selectedApplyActivity && isActivityEnded(selectedApplyActivity)"
            type="error"
            :closable="false"
            style="margin-top: 6px;"
            title="活动已经散场（结束钟点已过或状态为已结束），新报名送不进来。"
          />
        </el-form-item>
        <el-form-item label="选择岗位" required>
          <el-select v-model="applyForm.positionId" placeholder="请选择岗位" @change="onPositionChange">
            <el-option v-for="p in positions" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="报名备注">
          <el-textarea v-model="applyForm.applyMessage" />
        </el-form-item>
      </el-form>

      <div v-if="capabilityCheckResult" style="margin-top: 20px; padding: 15px; border-radius: 4px;" :style="{ backgroundColor: capabilityCheckResult.pass ? '#f6ffed' : '#fff2f0' }">
        <h4 style="margin: 0 0 10px 0;">能力校验结果：{{ capabilityCheckResult.pass ? '通过' : '未通过' }}</h4>
        <div v-if="capabilityCheckResult.skillCheck?.length">
          <div v-for="(item, index) in capabilityCheckResult.skillCheck" :key="index" :style="{ color: item.includes('通过') ? '#52c41a' : '#ff4d4f' }">
            • {{ item }}
          </div>
        </div>
        <div v-if="capabilityCheckResult.certCheck?.length">
          <div v-for="(item, index) in capabilityCheckResult.certCheck" :key="index" :style="{ color: item.includes('通过') ? '#52c41a' : '#ff4d4f' }">
            • {{ item }}
          </div>
        </div>
        <div :style="{ color: capabilityCheckResult.hoursCheck?.includes('通过') ? '#52c41a' : '#ff4d4f' }">
          • {{ capabilityCheckResult.hoursCheck }}
        </div>
      </div>

      <template #footer>
        <el-button @click="applyModalVisible = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="!capabilityCheckResult?.pass || applyActivityEnded"
          @click="submitApply"
        >确认报名</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailModalVisible" title="报名详情" width="700px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="志愿者">{{ selectedRegistration?.volunteerName }}</el-descriptions-item>
        <el-descriptions-item label="联系方式">{{ selectedRegistration?.volunteerPhone }}</el-descriptions-item>
        <el-descriptions-item label="活动">{{ selectedRegistration?.activityName }}</el-descriptions-item>
        <el-descriptions-item label="岗位">{{ selectedRegistration?.positionName }}</el-descriptions-item>
        <el-descriptions-item label="能力校验">{{ selectedRegistration?.checkPassDesc }}</el-descriptions-item>
        <el-descriptions-item label="门槛版本">
          报名时 v{{ selectedRegistration?.requirementVersionAtApply ?? 1 }}
          <span v-if="selectedRegistration?.currentRequirementVersion &&
                      selectedRegistration.currentRequirementVersion !== selectedRegistration?.requirementVersionAtApply">
            → 当前 v{{ selectedRegistration.currentRequirementVersion }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="有效校验结论">
          <el-tag :type="selectedRegistration?.effectivePass === 1 ? 'success' : 'danger'" size="small">
            {{ selectedRegistration?.effectivePassDesc }}
          </el-tag>
          <span v-if="selectedRegistration?.recheckPass !== null && selectedRegistration?.recheckPass !== undefined"
                style="margin-left: 8px; font-size: 12px; color: #909399;">以最新门槛复核为准</span>
        </el-descriptions-item>
        <el-descriptions-item label="当前节点">{{ selectedRegistration?.currentApprovalNodeDesc }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ selectedRegistration?.statusDesc }}</el-descriptions-item>
        <el-descriptions-item label="报名时间">{{ selectedRegistration?.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ selectedRegistration?.applyMessage }}</el-descriptions-item>
      </el-descriptions>

      <el-row :gutter="12" style="margin-top: 20px;">
        <el-col :span="selectedRegistration?.recheckResult ? 12 : 24">
          <h4 style="margin: 0 0 10px 0;">
            报名当时校验
            <el-tag size="small" type="info" style="margin-left: 6px;">
              门槛 v{{ selectedRegistration?.requirementVersionAtApply ?? 1 }}
            </el-tag>
          </h4>
          <div style="padding: 15px; background-color: #f5f5f5; border-radius: 4px; min-height: 120px;">
            <div v-if="checkResultData?.skillCheck?.length">
              <div v-for="(item, index) in checkResultData.skillCheck" :key="'s'+index" :style="{ color: item.includes('通过') ? '#52c41a' : '#ff4d4f' }">
                • {{ item }}
              </div>
            </div>
            <div v-if="checkResultData?.certCheck?.length">
              <div v-for="(item, index) in checkResultData.certCheck" :key="'c'+index" :style="{ color: item.includes('通过') ? '#52c41a' : '#ff4d4f' }">
                • {{ item }}
              </div>
            </div>
            <div v-if="checkResultData" :style="{ color: checkResultData.hoursCheck?.includes('通过') ? '#52c41a' : '#ff4d4f' }">
              • {{ checkResultData.hoursCheck }}
            </div>
          </div>
        </el-col>
        <el-col v-if="recheckResultData" :span="12">
          <h4 style="margin: 0 0 10px 0;">
            门槛改完后复核
            <el-tag size="small" :type="selectedRegistration?.recheckPass === 1 ? 'success' : 'danger'" style="margin-left: 6px;">
              v{{ selectedRegistration?.currentRequirementVersion }} · {{ selectedRegistration?.recheckPassDesc }}
            </el-tag>
          </h4>
          <div style="padding: 15px; border-radius: 4px; min-height: 120px;"
               :style="{ backgroundColor: selectedRegistration?.recheckPass === 1 ? '#f6ffed' : '#fff2f0' }">
            <div v-if="recheckResultData?.skillCheck?.length">
              <div v-for="(item, index) in recheckResultData.skillCheck" :key="'rs'+index" :style="{ color: item.includes('通过') ? '#52c41a' : '#ff4d4f' }">
                • {{ item }}
              </div>
            </div>
            <div v-if="recheckResultData?.certCheck?.length">
              <div v-for="(item, index) in recheckResultData.certCheck" :key="'rc'+index" :style="{ color: item.includes('通过') ? '#52c41a' : '#ff4d4f' }">
                • {{ item }}
              </div>
            </div>
            <div :style="{ color: recheckResultData.hoursCheck?.includes('通过') ? '#52c41a' : '#ff4d4f' }">
              • {{ recheckResultData.hoursCheck }}
            </div>
            <el-alert
              v-if="selectedRegistration?.recheckPass === 0"
              type="error"
              :closable="false"
              style="margin-top: 10px;"
              :title="selectedRegistration.status === 4 || selectedRegistration.status === 1
                ? '已批完不清退，但该人员不计入岗位满员人数'
                : '在途单已停在能力校验失败并让出名额，通过动作不成立'"
            />
          </div>
        </el-col>
      </el-row>

      <div style="margin-top: 20px;">
        <h4>审批流程</h4>
        <div class="approval-flow">
          <div class="flow-steps">
            <div v-for="(flow, index) in selectedRegistration?.approvalFlows" :key="flow.id" class="flow-step">
              <div class="step-circle" :class="getStepClass(flow)">
                <el-icon v-if="flow.status === 1"><Check /></el-icon>
                <el-icon v-else-if="flow.status === 2"><Close /></el-icon>
                <el-icon v-else-if="flow.status === 3"><RefreshRight /></el-icon>
                <span v-else>{{ index + 1 }}</span>
              </div>
              <div class="step-name">{{ flow.nodeName }}</div>
              <div class="step-status">{{ flow.statusDesc }}</div>
              <div class="step-approver" v-if="flow.approverName">{{ flow.approverName }}</div>
              <div class="step-comment" v-if="flow.comment">{{ flow.comment }}</div>
              <div class="step-time" v-if="flow.updatedAt">{{ flow.updatedAt }}</div>
              <div v-if="index < (selectedRegistration?.approvalFlows.length || 0) - 1" class="step-line" :class="getStepClass(flow)"></div>
            </div>
          </div>
        </div>
        <el-alert
          v-if="selectedRegistration?.status === 5 && selectedRegistration?.blockReason === 'ACTIVITY'"
          type="error"
          :closable="false"
          style="margin-top: 10px;"
          title="活动已经散场：还没批完的通过一律不成立，名额已让出。"
        />
        <el-alert
          v-else-if="selectedRegistration?.status === 5 && selectedRegistration?.blockReason === 'CERT'"
          type="error"
          :closable="false"
          style="margin-top: 10px;"
          title="所需证书已过有效期：待审停住、通过不成立，名额已让出。"
        />
        <el-alert
          v-else-if="selectedRegistration?.status === 5"
          type="error"
          :closable="false"
          style="margin-top: 10px;"
          title="该单在岗位门槛更新后复核失败，已停在能力校验失败并让出名额，待门槛放宽重检通过后才能继续审批。"
        />
        <el-alert
          v-else-if="selectedRegistration?.activityOngoing === false"
          type="error"
          :closable="false"
          style="margin-top: 10px;"
          title="活动已经散场，该单今晚不再计入排班，通过动作不成立。"
        />
        <el-alert
          v-else-if="(selectedRegistration?.expiredCertificates || []).length > 0"
          type="error"
          :closable="false"
          style="margin-top: 10px;"
          :title="`所需证书已过有效期（${(selectedRegistration?.expiredCertificates || []).join('、')}），今晚不计入排班。`"
        />
      </div>

      <div style="margin-top: 20px;" v-if="selectedRegistration?.status === 3">
        <el-alert
          type="warning"
          :closable="false"
          style="margin-bottom: 10px;"
          title="该单处于退回修改状态：当前不占岗位名额；重新送审后能力校验与组长、负责人审批全部从头来。"
        />
        <el-button type="warning" @click="openResubmitModal(selectedRegistration)">重新送审</el-button>
      </div>
    </el-dialog>

    <el-dialog v-model="resubmitModalVisible" title="退回修改 · 重新送审" width="600px">
      <el-alert
        type="info"
        :closable="false"
        style="margin-bottom: 16px;"
        title="重新送审将按岗位当前最新门槛重跑能力校验；通过则从组长审批节点开始，两个审批节点全部重走，不接回退回前的节点。"
      />
      <el-form label-width="100px">
        <el-form-item label="志愿者">{{ resubmitTarget?.volunteerName }}</el-form-item>
        <el-form-item label="岗位">{{ resubmitTarget?.positionName }}</el-form-item>
        <el-form-item label="补充备注">
          <el-input v-model="resubmitMessage" type="textarea" :rows="3" placeholder="可补充修改说明" />
        </el-form-item>
      </el-form>
      <div v-if="resubmitCheck" style="padding: 15px; border-radius: 4px;"
           :style="{ backgroundColor: resubmitCheck.pass ? '#f6ffed' : '#fff2f0' }">
        <h4 style="margin: 0 0 10px 0;">最新门槛能力校验：{{ resubmitCheck.pass ? '通过' : '未通过' }}</h4>
        <div v-if="resubmitCheck.certCheck?.length">
          <div v-for="(item, index) in resubmitCheck.certCheck" :key="index"
               :style="{ color: item.includes('通过') ? '#52c41a' : '#ff4d4f' }">• {{ item }}</div>
        </div>
        <div v-if="resubmitCheck.skillCheck?.length">
          <div v-for="(item, index) in resubmitCheck.skillCheck" :key="index"
               :style="{ color: item.includes('通过') ? '#52c41a' : '#ff4d4f' }">• {{ item }}</div>
        </div>
        <div :style="{ color: resubmitCheck.hoursCheck?.includes('通过') ? '#52c41a' : '#ff4d4f' }">
          • {{ resubmitCheck.hoursCheck }}
        </div>
      </div>
      <el-alert
        v-else
        type="info"
        :closable="false"
        title="点击重新送审后，系统将按岗位当前最新门槛做权威能力校验；不通过会停在能力校验失败且不占名额。"
      />
      <template #footer>
        <el-button @click="resubmitModalVisible = false">取消</el-button>
        <el-button type="warning" :loading="resubmitting" @click="doResubmit">重新送审</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Check, Close, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { registrationApi, activityApi, positionApi, volunteerApi, type RegistrationDetail, type Activity, type Position, type Volunteer, type CapabilityCheckResult, type RegistrationRequest } from '@/api'

const registrations = ref<RegistrationDetail[]>([])
const activities = ref<Activity[]>([])
const positions = ref<Position[]>([])
const volunteers = ref<Volunteer[]>([])
const applyModalVisible = ref(false)
const detailModalVisible = ref(false)
const selectedRegistration = ref<RegistrationDetail | null>(null)
const checkResultData = ref<CapabilityCheckResult | null>(null)
const recheckResultData = ref<CapabilityCheckResult | null>(null)
const resubmitModalVisible = ref(false)
const resubmitTarget = ref<RegistrationDetail | null>(null)
const resubmitMessage = ref('')
const resubmitCheck = ref<CapabilityCheckResult | null>(null)
const resubmitting = ref(false)
const filterStatus = ref(-1)
const filterCheckPass = ref(-1)

const applyForm = ref<RegistrationRequest>({
  volunteerId: 0,
  activityId: 0,
  positionId: 0,
  applyMessage: ''
})

const capabilityCheckResult = ref<CapabilityCheckResult | null>(null)

// 活动散场口径与后端 GateRules 一致：状态非进行中，或结束钟点已过
const isActivityEnded = (a: Activity) =>
  a.status !== 1 || (!!a.endTime && new Date(a.endTime.replace(' ', 'T')) < new Date())

const selectedApplyActivity = computed(() =>
  activities.value.find(a => a.id === applyForm.value.activityId) || null)
const applyActivityEnded = computed(() =>
  !!selectedApplyActivity.value && isActivityEnded(selectedApplyActivity.value))

const loadRegistrations = async () => {
  let res
  if (filterStatus.value === -1 && filterCheckPass.value === -1) {
    res = await registrationApi.getAll()
  } else if (filterStatus.value !== -1) {
    res = await registrationApi.getByStatus(filterStatus.value)
  } else {
    res = await registrationApi.getByCheckPass(filterCheckPass.value)
  }
  registrations.value = res.data || []
}

const loadActivities = async () => {
  const res = await activityApi.getAll()
  activities.value = res.data || []
}

const loadVolunteers = async () => {
  const res = await volunteerApi.getAll()
  volunteers.value = res.data || []
}

const onActivityChange = async () => {
  if (applyForm.value.activityId) {
    const res = await positionApi.getByActivity(applyForm.value.activityId)
    positions.value = res.data || []
    applyForm.value.positionId = 0
    capabilityCheckResult.value = null
  }
}

const onVolunteerChange = () => {
  capabilityCheckResult.value = null
}

const onPositionChange = async () => {
  if (applyForm.value.volunteerId && applyForm.value.positionId) {
    const res = await registrationApi.checkCapability({
      volunteerId: applyForm.value.volunteerId,
      activityId: applyForm.value.activityId,
      positionId: applyForm.value.positionId,
      applyMessage: ''
    })
    capabilityCheckResult.value = res.data || null
  }
}

const openApplyModal = () => {
  applyForm.value = {
    volunteerId: 0,
    activityId: 0,
    positionId: 0,
    applyMessage: ''
  }
  capabilityCheckResult.value = null
  positions.value = []
  applyModalVisible.value = true
}

const submitApply = async () => {
  try {
    await registrationApi.create(applyForm.value)
    applyModalVisible.value = false
    loadRegistrations()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      || '报名失败'
    ElMessage.error(msg)
  }
}

const viewDetail = (registration: RegistrationDetail) => {
  selectedRegistration.value = registration
  checkResultData.value = registration.capabilityCheckResult
    ? JSON.parse(registration.capabilityCheckResult)
    : null
  recheckResultData.value = registration.recheckResult
    ? JSON.parse(registration.recheckResult)
    : null
  detailModalVisible.value = true
}

const openResubmitModal = (registration: RegistrationDetail) => {
  resubmitTarget.value = registration
  resubmitMessage.value = registration.applyMessage || ''
  resubmitCheck.value = null
  resubmitModalVisible.value = true
}

const doResubmit = async () => {
  if (!resubmitTarget.value) return
  resubmitting.value = true
  try {
    const res = await registrationApi.resubmit(resubmitTarget.value.id, resubmitMessage.value)
    const detail = res.data
    // 后端按当前最新门槛完成权威重检，把结果展示出来
    resubmitCheck.value = detail.capabilityCheckResult
      ? JSON.parse(detail.capabilityCheckResult)
      : null
    if (detail.status === 2) {
      ElMessage.error('最新门槛能力校验未通过，已驳回，未占用岗位名额')
      resubmitting.value = false
      return // 保留弹窗展示未通过明细
    }
    ElMessage.success('重新送审成功，能力校验与两个审批节点从头开始')
    resubmitModalVisible.value = false
    detailModalVisible.value = false
    await loadRegistrations()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message || '重新送审失败'
    ElMessage.error(msg)
  } finally {
    resubmitting.value = false
  }
}

const getStatusType = (status: number) => {
  switch (status) {
    case 0: return 'warning'
    case 1: return 'success'
    case 2: return 'danger'
    case 3: return 'info'
    case 4: return 'success'
    case 5: return 'danger'
    default: return 'info'
  }
}

const getStepClass = (flow: { status: number }) => {
  switch (flow.status) {
    case 1: return 'approved'
    case 2: return 'rejected'
    case 3: return 'returned'
    case 0: return 'pending'
    default: return 'pending'
  }
}

onMounted(() => {
  loadRegistrations()
  loadActivities()
  loadVolunteers()
})
</script>

<style scoped>
.registrations {
  padding: 20px;
}

.approval-flow {
  padding: 20px;
  background-color: #f5f5f5;
  border-radius: 4px;
}

.flow-steps {
  display: flex;
  align-items: center;
  justify-content: center;
}

.flow-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
}

.step-circle {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background-color: #e0e0e0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: bold;
  font-size: 16px;
}

.step-circle.approved {
  background-color: #52c41a;
}

.step-circle.rejected {
  background-color: #ff4d4f;
}

.step-circle.returned {
  background-color: #fa8c16;
}

.step-circle.pending {
  background-color: #1890ff;
}

.step-name {
  margin-top: 8px;
  font-weight: bold;
}

.step-status {
  margin-top: 4px;
  font-size: 12px;
  color: #666;
}

.step-approver {
  margin-top: 4px;
  font-size: 12px;
  color: #999;
}

.step-comment {
  margin-top: 4px;
  font-size: 12px;
  color: #fa8c16;
  max-width: 150px;
  text-align: center;
}

.step-time {
  margin-top: 4px;
  font-size: 11px;
  color: #ccc;
}

.step-line {
  width: 80px;
  height: 3px;
  background-color: #e0e0e0;
  margin: 0 20px;
  flex-shrink: 0;
}

.step-line.approved {
  background-color: #52c41a;
}

.step-line.rejected {
  background-color: #ff4d4f;
}

.step-line.returned {
  background-color: #fa8c16;
}

.step-line.pending {
  background-color: #d9d9d9;
}
</style>