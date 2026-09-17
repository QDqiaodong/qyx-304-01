<template>
  <div class="approvals">
    <el-card>
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
        <h3>审批管理</h3>
      </div>
      <div style="display: flex; gap: 20px; margin-bottom: 20px;">
        <el-select v-model="filterNodeLevel" placeholder="审批节点">
          <el-option :label="0" :value="-1">全部</el-option>
          <el-option :label="1" :value="1">组长审批</el-option>
          <el-option :label="2" :value="2">负责人审批</el-option>
        </el-select>
        <el-select v-model="filterStatus" placeholder="全部状态">
          <el-option label="全部" :value="-1" />
          <el-option label="待审批" :value="0" />
          <el-option label="已通过" :value="1" />
          <el-option label="已驳回" :value="2" />
          <el-option label="退回修改" :value="3" />
          <el-option label="审批完成" :value="4" />
          <el-option label="能力校验失败" :value="5" />
        </el-select>
        <el-button @click="loadApprovals">查询</el-button>
      </div>

      <div style="display: flex; gap: 20px; margin-bottom: 20px;">
        <el-statistic title="待审批数量" :value="pendingCount" />
        <el-statistic title="已通过数量" :value="approvedCount" />
        <el-statistic title="已驳回数量" :value="rejectedCount" />
      </div>

      <el-table :data="approvals" border :row-class-name="getRowClass">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="volunteerName" label="志愿者" />
        <el-table-column prop="volunteerPhone" label="联系方式" />
        <el-table-column prop="activityName" label="活动" />
        <el-table-column prop="positionName" label="岗位" />
        <el-table-column label="能力校验" width="150">
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
        <el-table-column label="时效" width="130">
          <template #default="scope">
            <el-tag v-if="scope.row.certExpired" type="danger" size="small" effect="dark">
              证件已过期
            </el-tag>
            <el-tag v-if="scope.row.activityEnded" type="info" size="small" effect="dark"
                    :style="scope.row.certExpired ? 'margin-left:4px' : ''">
              活动已散场
            </el-tag>
            <span v-if="!scope.row.certExpired && !scope.row.activityEnded"
                  style="font-size: 12px; color: #52c41a;">正常</span>
          </template>
        </el-table-column>
        <el-table-column prop="currentApprovalNodeDesc" label="当前节点" width="120" />
        <el-table-column prop="statusDesc" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)">
              {{ scope.row.statusDesc }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="报名时间" />
        <el-table-column label="审批进度" width="200">
          <template #default="scope">
            <div class="progress-bar">
              <div class="progress-track">
                <div class="progress-fill" :style="{ width: getProgressWidth(scope.row) + '%' }"></div>
              </div>
              <div class="progress-nodes">
                <div v-for="(node, index) in approvalNodes" :key="index" 
                     class="progress-node" :class="getNodeClass(scope.row, index)">
                  <span>{{ node.name }}</span>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300">
          <template #default="scope">
            <el-button size="small" @click="viewDetail(scope.row)">详情</el-button>
            <el-button v-if="canApprove(scope.row)" size="small" type="primary" @click="openApprovalModal(scope.row)">审批</el-button>
            <el-tag v-else-if="scope.row.timeBlocked" type="danger" size="small">
              {{ scope.row.blockReason }}·通过已锁死
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="detailModalVisible" title="审批详情" width="700px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="志愿者">{{ selectedApproval?.volunteerName }}</el-descriptions-item>
        <el-descriptions-item label="联系方式">{{ selectedApproval?.volunteerPhone }}</el-descriptions-item>
        <el-descriptions-item label="活动">{{ selectedApproval?.activityName }}</el-descriptions-item>
        <el-descriptions-item label="岗位">{{ selectedApproval?.positionName }}</el-descriptions-item>
        <el-descriptions-item label="能力校验">{{ selectedApproval?.checkPassDesc }}</el-descriptions-item>
        <el-descriptions-item label="门槛版本">
          报名时 v{{ selectedApproval?.requirementVersionAtApply ?? 1 }}
          <span v-if="selectedApproval?.currentRequirementVersion &&
                      selectedApproval.currentRequirementVersion !== selectedApproval?.requirementVersionAtApply">
            → 当前 v{{ selectedApproval.currentRequirementVersion }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item v-if="selectedApproval?.recheckPass !== null && selectedApproval?.recheckPass !== undefined" label="门槛复核">
          <el-tag :type="selectedApproval.recheckPass === 1 ? 'success' : 'danger'" size="small">
            {{ selectedApproval.recheckPassDesc }}
          </el-tag>
          <span v-if="selectedApproval.recheckPass === 0" style="margin-left: 6px; font-size: 12px; color: #ff4d4f;">
            {{ selectedApproval.status === 4 || selectedApproval.status === 1
              ? '保留审批结果，但不计满员人数'
              : '已停在能力校验失败并让出名额' }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="当前节点">{{ selectedApproval?.currentApprovalNodeDesc }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ selectedApproval?.statusDesc }}</el-descriptions-item>
        <el-descriptions-item label="报名时间">{{ selectedApproval?.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ selectedApproval?.applyMessage }}</el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="selectedApproval?.status === 5"
        type="error"
        :closable="false"
        style="margin-top: 12px;"
        title="岗位门槛已更新，该单复核失败并停在能力校验失败：通过动作不成立、名额已让出；门槛放宽重检通过后方可继续。"
      />
      <el-alert
        v-if="selectedApproval?.timeBlocked"
        :type="selectedApproval.certExpired ? 'error' : 'warning'"
        :closable="false"
        style="margin-top: 12px;"
        :title="`时效拦截：${selectedApproval.blockReason}。${selectedApproval.status === 4 || selectedApproval.status === 1
          ? '历史审批记录保留，但不计入今晚排班满员人数。'
          : '待审停住、通过不成立并已让出名额。'}`"
      />
      <el-alert
        v-else-if="selectedApproval?.status === 3"
        type="warning"
        :closable="false"
        style="margin-top: 12px;"
        title="该单已退回修改，重提前不占岗位名额；重新送审后两个审批节点全部从头来。"
      />
      <el-alert
        v-else-if="selectedApproval?.recheckPass === 0"
        type="error"
        :closable="false"
        style="margin-top: 12px;"
        title="最新门槛复核未通过，该人员不计入岗位满员人数。"
      />

      <div style="margin-top: 20px;" v-if="checkResultData || recheckResultData">
        <h4>能力校验对照</h4>
        <el-row :gutter="12">
          <el-col :span="recheckResultData ? 12 : 24">
            <div style="padding: 12px; background: #f5f5f5; border-radius: 4px; font-size: 13px;">
              <div style="font-weight: bold; margin-bottom: 6px;">
                报名当时校验（门槛 v{{ selectedApproval?.requirementVersionAtApply ?? 1 }}）
              </div>
              <template v-if="checkResultData">
                <div v-for="(item, index) in (checkResultData.skillCheck || [])" :key="'s'+index"
                     :style="{ color: item.includes('通过') ? '#52c41a' : '#ff4d4f' }">• {{ item }}</div>
                <div v-for="(item, index) in (checkResultData.certCheck || [])" :key="'c'+index"
                     :style="{ color: item.includes('通过') ? '#52c41a' : '#ff4d4f' }">• {{ item }}</div>
                <div :style="{ color: checkResultData.hoursCheck?.includes('通过') ? '#52c41a' : '#ff4d4f' }">
                  • {{ checkResultData.hoursCheck }}
                </div>
                <div v-if="checkResultData.activityCheck"
                     :style="{ color: checkResultData.activityCheck.includes('通过') ? '#52c41a' : '#ff4d4f' }">
                  • {{ checkResultData.activityCheck }}
                </div>
              </template>
            </div>
          </el-col>
          <el-col v-if="recheckResultData" :span="12">
            <div style="padding: 12px; border-radius: 4px; font-size: 13px;"
                 :style="{ background: selectedApproval?.recheckPass === 1 ? '#f6ffed' : '#fff2f0' }">
              <div style="font-weight: bold; margin-bottom: 6px;">
                门槛改完后复核（v{{ selectedApproval?.currentRequirementVersion }} ·
                {{ selectedApproval?.recheckPassDesc }}）
              </div>
              <div v-for="(item, index) in (recheckResultData.skillCheck || [])" :key="'rs'+index"
                   :style="{ color: item.includes('通过') ? '#52c41a' : '#ff4d4f' }">• {{ item }}</div>
              <div v-for="(item, index) in (recheckResultData.certCheck || [])" :key="'rc'+index"
                   :style="{ color: item.includes('通过') ? '#52c41a' : '#ff4d4f' }">• {{ item }}</div>
              <div :style="{ color: recheckResultData.hoursCheck?.includes('通过') ? '#52c41a' : '#ff4d4f' }">
                • {{ recheckResultData.hoursCheck }}
              </div>
              <div v-if="recheckResultData.activityCheck"
                   :style="{ color: recheckResultData.activityCheck.includes('通过') ? '#52c41a' : '#ff4d4f' }">
                • {{ recheckResultData.activityCheck }}
              </div>
            </div>
          </el-col>
        </el-row>
      </div>

      <div style="margin-top: 20px;">
        <h4>审批流程进度</h4>
        <div class="approval-progress">
          <div class="progress-container">
            <div class="progress-line">
              <div class="progress-line-fill" :style="{ width: currentProgress + '%' }"></div>
            </div>
            <div class="progress-steps">
              <div v-for="(node, index) in approvalNodes" :key="index" 
                   class="progress-step" :class="getStepClass(index)">
                <div class="step-indicator">
                  <el-icon v-if="getStepIcon(index) === 'check'"><Check /></el-icon>
                  <el-icon v-else-if="getStepIcon(index) === 'close'"><Close /></el-icon>
                  <el-icon v-else-if="getStepIcon(index) === 'refresh'"><RefreshRight /></el-icon>
                  <span v-else>{{ index + 1 }}</span>
                </div>
                <div class="step-label">{{ node.name }}</div>
                <div class="step-detail" v-if="getStepDetail(index)">
                  {{ getStepDetail(index) }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div style="margin-top: 20px;">
        <h4>审批记录</h4>
        <el-table :data="selectedApproval?.approvalFlows" border size="small" style="width: 100%;">
          <el-table-column prop="nodeName" label="审批节点" />
          <el-table-column prop="approverName" label="审批人" />
          <el-table-column prop="statusDesc" label="审批结果">
            <template #default="scope">
              <el-tag :type="scope.row.status === 1 ? 'success' : scope.row.status === 2 ? 'danger' : 'info'">
                {{ scope.row.statusDesc }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="comment" label="审批意见" />
          <el-table-column prop="updatedAt" label="审批时间" />
        </el-table>
      </div>
    </el-dialog>

    <el-dialog v-model="approvalModalVisible" title="审批操作" width="500px">
      <el-alert
        v-if="selectedApproval?.timeBlocked"
        type="error"
        :closable="false"
        style="margin-bottom: 16px;"
        :title="`${selectedApproval.blockReason}：通过不成立。在途单停在能力校验失败并让出名额；已批完者保留历史记录但不计满员。`"
      />
      <el-form :model="approvalForm" label-width="80px">
        <el-form-item label="审批人">
          <el-input v-model="approvalForm.approverName" />
        </el-form-item>
        <el-form-item label="审批意见">
          <el-textarea v-model="approvalForm.comment" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approvalModalVisible = false">取消</el-button>
        <el-button type="warning" :disabled="selectedApproval?.timeBlocked" @click="handleReturn">退回修改</el-button>
        <el-button type="danger" :disabled="selectedApproval?.timeBlocked" @click="handleReject">驳回</el-button>
        <el-button type="primary" :disabled="selectedApproval?.timeBlocked" @click="handlePass">通过</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Check, Close, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { registrationApi, approvalApi, type RegistrationDetail, type ApprovalRequest, type CapabilityCheckResult } from '@/api'

const approvals = ref<RegistrationDetail[]>([])
const selectedApproval = ref<RegistrationDetail | null>(null)
const checkResultData = ref<CapabilityCheckResult | null>(null)
const recheckResultData = ref<CapabilityCheckResult | null>(null)
const detailModalVisible = ref(false)
const approvalModalVisible = ref(false)
const filterNodeLevel = ref(-1)
const filterStatus = ref(-1)

const approvalNodes = [
  { level: 0, name: '能力校验' },
  { level: 1, name: '组长审批' },
  { level: 2, name: '负责人审批' },
  { level: 3, name: '审批完成' }
]

const approvalForm = ref<ApprovalRequest>({
  registrationId: 0,
  action: 1,
  comment: '',
  approverId: 0,
  approverName: ''
})

const pendingCount = computed(() => approvals.value.filter(a => a.status === 0).length)
const approvedCount = computed(() => approvals.value.filter(a => a.status === 1 || a.status === 4).length)
const rejectedCount = computed(() => approvals.value.filter(a => a.status === 2).length)

const currentProgress = computed(() => {
  if (!selectedApproval.value) return 0
  const flows = selectedApproval.value.approvalFlows || []
  const completedCount = flows.filter(f => f.status === 1).length
  return (completedCount / flows.length) * 100
})

const loadApprovals = async () => {
  let res
  if (filterStatus.value === -1) {
    res = await registrationApi.getAll()
  } else {
    res = await registrationApi.getByStatus(filterStatus.value)
  }
  const approvalData = res.data || []
  let filteredData = approvalData as RegistrationDetail[]
  if (filterNodeLevel.value !== -1) {
    filteredData = filteredData.filter((a: RegistrationDetail) => a.currentApprovalNode === filterNodeLevel.value)
  }
  approvals.value = filteredData
}

const viewDetail = (approval: RegistrationDetail) => {
  selectedApproval.value = approval
  checkResultData.value = approval.capabilityCheckResult ? JSON.parse(approval.capabilityCheckResult) : null
  recheckResultData.value = approval.recheckResult ? JSON.parse(approval.recheckResult) : null
  detailModalVisible.value = true
}

const openApprovalModal = (approval: RegistrationDetail) => {
  selectedApproval.value = approval
  approvalForm.value = {
    registrationId: approval.id,
    action: 1,
    comment: '',
    approverId: 0,
    approverName: ''
  }
  approvalModalVisible.value = true
}

const handlePass = async () => {
  approvalForm.value.action = 1
  try {
    const res = await approvalApi.pass(approvalForm.value)
    const result = res.data
    if (result?.success) {
      ElMessage.success(result.message || '审批通过')
      approvalModalVisible.value = false
      loadApprovals()
    } else {
      ElMessage.error(result?.message || '审批不成立')
    }
  } catch (e: unknown) {
    ElMessage.error('审批失败')
  }
}

const handleReject = async () => {
  approvalForm.value.action = 2
  const res = await approvalApi.reject(approvalForm.value)
  if (res.data?.success) {
    approvalModalVisible.value = false
    loadApprovals()
  } else {
    ElMessage.error(res.data?.message || '操作失败')
  }
}

const handleReturn = async () => {
  approvalForm.value.action = 3
  const res = await approvalApi.returnBack(approvalForm.value)
  if (res.data?.success) {
    ElMessage.success('已退回修改，该单已让出岗位名额')
    approvalModalVisible.value = false
    loadApprovals()
  } else {
    ElMessage.error(res.data?.message || '操作失败')
  }
}

const canApprove = (approval: RegistrationDetail) => {
  // 只有待审批、有效校验（新门槛复核优先）通过、且证件未过期/活动未散场的单才能批；
  // 退回修改未重提、能力校验失败（含复核失败）一律卡住「通过」
  return approval.status === 0 && approval.effectivePass === 1 && !approval.timeBlocked
}

const getRowClass = ({ row }: { row: RegistrationDetail }) => {
  // 列表上涂过期色：证件过期红、活动散场灰
  if (row.certExpired) return 'row-cert-expired'
  if (row.activityEnded) return 'row-activity-ended'
  return ''
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

const getProgressWidth = (approval: RegistrationDetail) => {
  const flows = approval.approvalFlows || []
  const completedCount = flows.filter(f => f.status === 1).length
  return flows.length > 0 ? (completedCount / flows.length) * 100 : 0
}

const getNodeClass = (approval: RegistrationDetail, index: number) => {
  const flows = approval.approvalFlows || []
  if (index >= flows.length) return 'pending'
  const flow = flows[index]
  switch (flow.status) {
    case 1: return 'approved'
    case 2: return 'rejected'
    case 3: return 'returned'
    default: return 'pending'
  }
}

const getStepClass = (index: number) => {
  if (!selectedApproval.value) return 'pending'
  const flows = selectedApproval.value.approvalFlows || []
  if (index >= flows.length) return 'pending'
  const flow = flows[index]
  switch (flow.status) {
    case 1: return 'approved'
    case 2: return 'rejected'
    case 3: return 'returned'
    default: return 'pending'
  }
}

const getStepIcon = (index: number) => {
  if (!selectedApproval.value) return 'pending'
  const flows = selectedApproval.value.approvalFlows || []
  if (index >= flows.length) return 'pending'
  const flow = flows[index]
  switch (flow.status) {
    case 1: return 'check'
    case 2: return 'close'
    case 3: return 'refresh'
    default: return 'pending'
  }
}

const getStepDetail = (index: number) => {
  if (!selectedApproval.value) return ''
  const flows = selectedApproval.value.approvalFlows || []
  if (index >= flows.length) return ''
  const flow = flows[index]
  if (!flow.approverName) return ''
  let detail = flow.approverName
  if (flow.comment) detail += ` - ${flow.comment}`
  return detail
}

onMounted(loadApprovals)
</script>

<style scoped>
.approvals {
  padding: 20px;
}

:deep(.el-table .row-cert-expired) {
  background-color: #fff1f0;
}

:deep(.el-table .row-cert-expired:hover > td) {
  background-color: #ffd8d6 !important;
}

:deep(.el-table .row-activity-ended) {
  background-color: #f4f4f5;
  color: #909399;
}

.progress-bar {
  width: 100%;
}

.progress-track {
  height: 6px;
  background-color: #e0e0e0;
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background-color: #52c41a;
  transition: width 0.3s ease;
}

.progress-nodes {
  display: flex;
  justify-content: space-between;
  margin-top: 4px;
}

.progress-node {
  font-size: 11px;
  color: #999;
}

.progress-node.approved {
  color: #52c41a;
}

.progress-node.rejected {
  color: #ff4d4f;
}

.progress-node.returned {
  color: #fa8c16;
}

.progress-node.pending {
  color: #1890ff;
}

.approval-progress {
  padding: 20px;
  background-color: #f5f5f5;
  border-radius: 4px;
}

.progress-container {
  position: relative;
}

.progress-line {
  position: absolute;
  top: 20px;
  left: 50px;
  right: 50px;
  height: 4px;
  background-color: #e0e0e0;
  z-index: 0;
}

.progress-line-fill {
  height: 100%;
  background-color: #52c41a;
  transition: width 0.3s ease;
}

.progress-steps {
  display: flex;
  justify-content: space-between;
  position: relative;
  z-index: 1;
}

.progress-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
}

.step-indicator {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background-color: #e0e0e0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: bold;
  font-size: 14px;
}

.progress-step.approved .step-indicator {
  background-color: #52c41a;
}

.progress-step.rejected .step-indicator {
  background-color: #ff4d4f;
}

.progress-step.returned .step-indicator {
  background-color: #fa8c16;
}

.progress-step.pending .step-indicator {
  background-color: #1890ff;
}

.step-label {
  margin-top: 8px;
  font-size: 14px;
  font-weight: bold;
}

.step-detail {
  margin-top: 4px;
  font-size: 12px;
  color: #666;
  text-align: center;
  max-width: 120px;
}
</style>