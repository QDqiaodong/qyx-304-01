<template>
  <div class="volunteers">
    <el-card>
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
        <h3>志愿者列表</h3>
        <el-button type="primary" @click="openModal">新建志愿者</el-button>
      </div>
      <el-table :data="volunteers" border>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="姓名" />
        <el-table-column prop="phone" label="手机号" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="totalHours" label="服务时长" width="100" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">
              {{ scope.row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250">
          <template #default="scope">
            <el-button size="small" @click="viewDetail(scope.row)">详情</el-button>
            <el-button size="small" @click="editVolunteer(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteVolunteer(scope.row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑志愿者' : '新建志愿者'" width="600px">
      <el-form :model="formData" label-width="100px">
        <el-form-item label="姓名" required>
          <el-input v-model="formData.name" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="formData.phone" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="formData.email" />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="formData.idCard" />
        </el-form-item>
        <el-form-item label="服务时长">
          <el-input-number v-model="formData.totalHours" :min="0" />
          <span style="margin-left: 8px;">小时</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="formData.status">
            <el-option :label="1" :value="1">正常</el-option>
            <el-option :label="0" :value="0">禁用</el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="志愿者详情" width="700px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="姓名">{{ selectedVolunteer?.name }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ selectedVolunteer?.phone }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ selectedVolunteer?.email }}</el-descriptions-item>
        <el-descriptions-item label="身份证号">{{ selectedVolunteer?.idCard }}</el-descriptions-item>
        <el-descriptions-item label="服务时长">{{ selectedVolunteer?.totalHours }} 小时</el-descriptions-item>
        <el-descriptions-item label="状态">{{ selectedVolunteer?.status === 1 ? '正常' : '禁用' }}</el-descriptions-item>
      </el-descriptions>

      <div style="margin-top: 20px;">
        <h4>技能信息</h4>
        <el-table :data="volunteerSkills" border size="small" style="width: 100%;">
          <el-table-column prop="skillName" label="技能名称" />
          <el-table-column prop="skillLevel" label="技能等级" />
          <el-table-column prop="createdAt" label="添加时间" />
        </el-table>
        <el-button size="small" style="margin-top: 10px;" @click="openSkillModal">添加技能</el-button>
      </div>

      <div style="margin-top: 20px;">
        <h4>证书信息</h4>
        <el-table :data="volunteerCertificates" border size="small" style="width: 100%;"
                  :row-class-name="certRowClass">
          <el-table-column prop="certName" label="证书名称" />
          <el-table-column prop="certNo" label="证书编号" />
          <el-table-column prop="issueDate" label="颁发日期" />
          <el-table-column label="有效期至" width="220">
            <template #default="scope">
              <span :style="{ color: isCertExpired(scope.row) ? '#ff4d4f' : '#303133', fontWeight: 600 }">
                {{ scope.row.expireDate || '长期有效' }}
              </span>
              <el-tag v-if="isCertExpired(scope.row)" type="danger" size="small" style="margin-left: 6px;">
                已过期
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="scope">
              <el-button size="small" type="primary" link @click="openEditCertModal(scope.row)">
                编辑有效期
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button size="small" style="margin-top: 10px;" @click="openCertModal">添加证书</el-button>
        <el-alert
          type="info"
          :closable="false"
          style="margin-top: 8px;"
          title="改证书有效期会同事务立即重检持证人的在途/已批报名：改后已过期则通过不成立并立即腾位，续期后被卡的单回到被卡前节点。"
        />
      </div>
    </el-dialog>

    <el-dialog v-model="skillModalVisible" title="添加技能" width="400px">
      <el-form :model="skillForm" label-width="80px">
        <el-form-item label="技能名称" required>
          <el-input v-model="skillForm.skillName" />
        </el-form-item>
        <el-form-item label="技能等级">
          <el-input-number v-model="skillForm.skillLevel" :min="1" :max="5" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="skillModalVisible = false">取消</el-button>
        <el-button type="primary" @click="addSkill">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="certModalVisible" title="添加证书" width="400px">
      <el-form :model="certForm" label-width="80px">
        <el-form-item label="证书名称" required>
          <el-input v-model="certForm.certName" />
        </el-form-item>
        <el-form-item label="证书编号">
          <el-input v-model="certForm.certNo" />
        </el-form-item>
        <el-form-item label="颁发日期">
          <el-date-picker v-model="certForm.issueDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="有效期至">
          <el-date-picker v-model="certForm.expireDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="certModalVisible = false">取消</el-button>
        <el-button type="primary" @click="addCertificate">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editCertModalVisible" title="编辑证书有效期" width="400px">
      <el-alert
        type="warning"
        :closable="false"
        style="margin-bottom: 12px;"
        title="保存后立即重检持证人全部在途/已批报名，今晚排班人数随之变动。"
      />
      <el-form :model="editCertForm" label-width="80px">
        <el-form-item label="证书名称">
          <el-input v-model="editCertForm.certName" />
        </el-form-item>
        <el-form-item label="证书编号">
          <el-input v-model="editCertForm.certNo" />
        </el-form-item>
        <el-form-item label="颁发日期">
          <el-date-picker v-model="editCertForm.issueDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="有效期至">
          <el-date-picker v-model="editCertForm.expireDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editCertModalVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCertificate">保存并重检</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { volunteerApi, type Volunteer, type VolunteerSkill, type VolunteerCertificate } from '@/api'

const volunteers = ref<Volunteer[]>([])
const dialogVisible = ref(false)
const detailVisible = ref(false)
const isEdit = ref(false)
const selectedVolunteer = ref<Volunteer | null>(null)
const volunteerSkills = ref<VolunteerSkill[]>([])
const volunteerCertificates = ref<VolunteerCertificate[]>([])

const formData = ref<Volunteer>({
  id: 0,
  name: '',
  phone: '',
  email: '',
  idCard: '',
  totalHours: 0,
  status: 1,
  createdAt: '',
  updatedAt: ''
})

const skillModalVisible = ref(false)
const skillForm = ref<VolunteerSkill>({
  id: 0,
  volunteerId: 0,
  skillName: '',
  skillLevel: 1,
  createdAt: ''
})

const certModalVisible = ref(false)
const certForm = ref<VolunteerCertificate>({
  id: 0,
  volunteerId: 0,
  certName: '',
  certNo: '',
  issueDate: '',
  expireDate: '',
  createdAt: ''
})

const editCertModalVisible = ref(false)
const editCertForm = ref<VolunteerCertificate>({
  id: 0,
  volunteerId: 0,
  certName: '',
  certNo: '',
  issueDate: '',
  expireDate: '',
  createdAt: ''
})

// 过期色：有效期早于今天即过期（与后端 GateRules 同日口径）
const isCertExpired = (cert: VolunteerCertificate) =>
  !!cert.expireDate && cert.expireDate < new Date().toISOString().slice(0, 10)

const certRowClass = ({ row }: { row: VolunteerCertificate }) =>
  isCertExpired(row) ? 'cert-expired-row' : ''

const loadVolunteers = async () => {
  const res = await volunteerApi.getAll()
  volunteers.value = res.data || []
}

const openModal = () => {
  isEdit.value = false
  formData.value = {
    id: 0,
    name: '',
    phone: '',
    email: '',
    idCard: '',
    totalHours: 0,
    status: 1,
    createdAt: '',
    updatedAt: ''
  }
  dialogVisible.value = true
}

const editVolunteer = (volunteer: Volunteer) => {
  isEdit.value = true
  formData.value = { ...volunteer }
  dialogVisible.value = true
}

const deleteVolunteer = async (id: number) => {
  await volunteerApi.delete(id)
  loadVolunteers()
}

const submitForm = async () => {
  if (isEdit.value) {
    await volunteerApi.update(formData.value.id, formData.value)
  } else {
    await volunteerApi.create(formData.value)
  }
  dialogVisible.value = false
  loadVolunteers()
}

const viewDetail = async (volunteer: Volunteer) => {
  selectedVolunteer.value = volunteer
  const [skillsRes, certsRes] = await Promise.all([
    volunteerApi.getSkills(volunteer.id),
    volunteerApi.getCertificates(volunteer.id)
  ])
  volunteerSkills.value = skillsRes.data || []
  volunteerCertificates.value = certsRes.data || []
  detailVisible.value = true
}

const openSkillModal = () => {
  skillForm.value = {
    id: 0,
    volunteerId: selectedVolunteer.value?.id || 0,
    skillName: '',
    skillLevel: 1,
    createdAt: ''
  }
  skillModalVisible.value = true
}

const openCertModal = () => {
  certForm.value = {
    id: 0,
    volunteerId: selectedVolunteer.value?.id || 0,
    certName: '',
    certNo: '',
    issueDate: '',
    expireDate: '',
    createdAt: ''
  }
  certModalVisible.value = true
}

const addSkill = async () => {
  await volunteerApi.addSkill(selectedVolunteer.value?.id || 0, skillForm.value)
  skillModalVisible.value = false
  viewDetail(selectedVolunteer.value!)
}

const addCertificate = async () => {
  await volunteerApi.addCertificate(selectedVolunteer.value?.id || 0, certForm.value)
  certModalVisible.value = false
  viewDetail(selectedVolunteer.value!)
}

const openEditCertModal = (cert: VolunteerCertificate) => {
  editCertForm.value = { ...cert }
  editCertModalVisible.value = true
}

const saveCertificate = async () => {
  const volunteerId = selectedVolunteer.value?.id || 0
  await volunteerApi.updateCertificate(volunteerId, editCertForm.value.id, editCertForm.value)
  editCertModalVisible.value = false
  await viewDetail(selectedVolunteer.value!)
  loadVolunteers()
}

onMounted(loadVolunteers)
</script>

<style scoped>
.volunteers {
  padding: 20px;
}

:deep(.cert-expired-row) {
  background-color: #fff2f0 !important;
}

:deep(.cert-expired-row td) {
  color: #ff4d4f;
}
</style>