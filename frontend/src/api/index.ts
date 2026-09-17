import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

/** 后端统一响应体 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/**
 * 响应拦截器直接返回 response.data（即后端 ApiResponse 体）。
 * 因此所有请求方法的“响应”类型就是 ApiResponse<T>，而不是 AxiosResponse 包装。
 */
interface TypedAxios {
  get<T>(url: string): Promise<ApiResponse<T>>
  post<T>(url: string, data?: unknown): Promise<ApiResponse<T>>
  put<T>(url: string, data?: unknown): Promise<ApiResponse<T>>
  delete<T>(url: string): Promise<ApiResponse<T>>
}

const http = api as unknown as TypedAxios

api.interceptors.response.use(
  (response) => {
    return response.data
  },
  (error) => {
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

export interface Activity {
  id: number
  name: string
  description: string
  startTime: string
  endTime: string
  location: string
  status: number
  createdAt: string
  updatedAt: string
}

export interface Position {
  id: number
  activityId: number
  name: string
  requiredSkills: string
  requiredCertificates: string
  requiredHours: number
  minCount: number
  maxCount: number
  requirementVersion?: number
  /** 实际占编人数（退回未重提、复核失败均不计入） */
  occupiedCount?: number
  /** 是否满员：occupiedCount >= maxCount */
  full?: boolean
  status: number
  createdAt: string
  updatedAt: string
}

export interface Volunteer {
  id: number
  name: string
  phone: string
  email: string
  idCard: string
  totalHours: number
  status: number
  createdAt: string
  updatedAt: string
}

export interface VolunteerSkill {
  id: number
  volunteerId: number
  skillName: string
  skillLevel: number
  createdAt: string
}

export interface VolunteerCertificate {
  id: number
  volunteerId: number
  certName: string
  certNo: string
  issueDate: string
  expireDate: string
  createdAt: string
}

export interface RegistrationDetail {
  id: number
  volunteerName: string
  volunteerPhone: string
  activityName: string
  positionName: string
  applyMessage: string
  status: number
  statusDesc: string
  checkPass: number
  checkPassDesc: string
  capabilityCheckResult: string
  requirementVersionAtApply?: number
  recheckResult?: string | null
  recheckPass?: number | null
  recheckPassDesc?: string
  currentRequirementVersion?: number
  effectivePass?: number
  effectivePassDesc?: string
  /** 停在能力校验失败的原因：THRESHOLD 门槛 / CERT 证件过期 / ACTIVITY 活动散场 */
  blockReason?: string | null
  /** 所属活动此刻是否仍在办 */
  activityOngoing?: boolean
  /** 岗位所需证书中此刻已过期的证书名称 */
  expiredCertificates?: string[]
  /** 排班今晚是否可到岗（活动在办且证件有效） */
  rosterEligible?: boolean
  currentApprovalNode: number
  currentApprovalNodeDesc: string
  approvalFlows: ApprovalFlowDetail[]
  createdAt: string
  updatedAt: string
}

export interface ApprovalFlowDetail {
  id: number
  nodeLevel: number
  nodeName: string
  approverId: number
  approverName: string
  status: number
  statusDesc: string
  comment: string
  createdAt: string
  updatedAt: string
}

export interface CapabilityCheckResult {
  pass: boolean
  skillCheck: string[]
  certCheck: string[]
  /** 本次校验时已过期的岗位所需证书名称 */
  expiredCertificates?: string[]
  hoursCheck: string
  message: string
}

export interface RegistrationRequest {
  volunteerId: number
  activityId: number
  positionId: number
  applyMessage: string
}

export interface ApprovalRequest {
  registrationId: number
  action: number
  comment: string
  approverId: number
  approverName: string
}

export const activityApi = {
  getAll: () => http.get<Activity[]>('/activities'),
  getById: (id: number) => http.get<Activity>(`/activities/${id}`),
  create: (data: Activity) => http.post<Activity>('/activities', data),
  update: (id: number, data: Activity) => http.put<Activity>(`/activities/${id}`, data),
  delete: (id: number) => http.delete<null>(`/activities/${id}`)
}

export const positionApi = {
  getAll: () => http.get<Position[]>('/positions'),
  getByActivity: (activityId: number) => http.get<Position[]>(`/positions/activity/${activityId}`),
  getById: (id: number) => http.get<Position>(`/positions/${id}`),
  create: (data: Position) => http.post<Position>('/positions', data),
  update: (id: number, data: Position) => http.put<Position>(`/positions/${id}`, data),
  delete: (id: number) => http.delete<null>(`/positions/${id}`)
}

export const volunteerApi = {
  getAll: () => http.get<Volunteer[]>('/volunteers'),
  getById: (id: number) => http.get<{ volunteer: Volunteer; skills: VolunteerSkill[]; certificates: VolunteerCertificate[] }>(`/volunteers/${id}`),
  create: (data: Volunteer) => http.post<Volunteer>('/volunteers', data),
  update: (id: number, data: Volunteer) => http.put<Volunteer>(`/volunteers/${id}`, data),
  delete: (id: number) => http.delete<null>(`/volunteers/${id}`),
  addSkill: (id: number, data: VolunteerSkill) => http.post<VolunteerSkill>(`/volunteers/${id}/skills`, data),
  getSkills: (id: number) => http.get<VolunteerSkill[]>(`/volunteers/${id}/skills`),
  addCertificate: (id: number, data: VolunteerCertificate) => http.post<VolunteerCertificate>(`/volunteers/${id}/certificates`, data),
  /** 改证书（含有效期）：后端同事务重检持证人在途/已批报名 */
  updateCertificate: (id: number, certId: number, data: VolunteerCertificate) =>
    http.put<VolunteerCertificate>(`/volunteers/${id}/certificates/${certId}`, data),
  getCertificates: (id: number) => http.get<VolunteerCertificate[]>(`/volunteers/${id}/certificates`)
}

export const registrationApi = {
  getAll: () => http.get<RegistrationDetail[]>('/registrations'),
  getById: (id: number) => http.get<RegistrationDetail>(`/registrations/${id}`),
  getByStatus: (status: number) => http.get<RegistrationDetail[]>(`/registrations/status/${status}`),
  getByCheckPass: (checkPass: number) => http.get<RegistrationDetail[]>(`/registrations/checkPass/${checkPass}`),
  getPending: (nodeLevel: number) => http.get<RegistrationDetail[]>(`/registrations/pending/${nodeLevel}`),
  create: (data: RegistrationRequest) => http.post<RegistrationDetail>('/registrations', data),
  /** 退回修改后重新送审：能力校验与两个审批节点全部从头来 */
  resubmit: (id: number, applyMessage?: string) =>
    http.post<RegistrationDetail>(`/registrations/${id}/resubmit`, { applyMessage }),
  checkCapability: (data: RegistrationRequest) => http.post<CapabilityCheckResult>('/registrations/check', data)
}

export const approvalApi = {
  getFlows: (registrationId: number) => http.get<ApprovalFlowDetail[]>(`/approvals/registration/${registrationId}`),
  getCurrent: (registrationId: number) => http.get<ApprovalFlowDetail>(`/approvals/registration/${registrationId}/current`),
  pass: (data: ApprovalRequest) => http.post<ApprovalActionResult>('/approvals/pass', data),
  reject: (data: ApprovalRequest) => http.post<ApprovalActionResult>('/approvals/reject', data),
  returnBack: (data: ApprovalRequest) => http.post<ApprovalActionResult>('/approvals/return', data)
}

export interface ApprovalActionResult {
  success: boolean
  message: string
}

export default api