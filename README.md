# qyx-304 公益志愿活动人员能力匹配流程审批系统

## 项目简介
公益志愿活动人员能力匹配流程审批系统，包含 Spring Boot 后端、Vue/Vite 前端、MySQL 和 Redis。

## 前端访问地址
- 默认地址：http://localhost:8204
- 127.0.0.1：http://127.0.0.1:8204

## 端口
- 前端：8204
- 后端 API：8304
- MySQL：3404
- Redis：6504

## 启动命令
```bash
sh start.sh
```

## 验证命令
```bash
cd backend && mvn compile -q
cd ../frontend && npm ci && npm run build
cd .. && docker compose up -d --build
curl -sS http://localhost:8204
curl -sS http://127.0.0.1:8204
```

## 门槛改写重检 + 退回重提（同一条验收）

岗位的技能 / 证书 / 服务时长门槛加严或改写，与在途报名的重检绑在同一事务、同一次提交里：

- **门槛写入即重检**：保存岗位时若门槛字段变化，`positions.requirement_version` 加 1，并在同一事务内
  对该岗位所有「在途 + 已批完」报名按新门槛复核（`registrations.recheck_result / recheck_pass`）。
  - 在途单（待审批）复核失败：停在新状态 **能力校验失败(5)**、记住被卡前节点、立即让出名额，
    此时任何「通过」动作直接失败；门槛放宽后复核通过会回到被卡前节点继续。
  - 已批完的人不清退，保留审批结果；但复核失败**不计入岗位满员人数**。
  - 详情并排展示「报名当时校验（门槛 v 版本）」与「门槛改完后复核」。
- **通过动作只信最新门槛**：通过时按「先岗位行锁、再报名行锁」加锁并现场复核。
  与门槛写入并发时账上只出现一种结局——重检已失败则通过不成立，或通过当时门槛仍能过；
  不会出现新门槛下不合格的人已显示批完。
- **退回重提从头跑**：`POST /api/registrations/{id}/resubmit`。退回后立即让出名额（退回单不占编），
  重提时能力校验与组长、负责人两个审批节点全部重建，绝不接回退回前节点；
  重提按当前门槛校验不通过则驳回、不占名额。
- **满员口径唯一**：岗位 `occupiedCount / full` 只统计状态为待审批/已通过/审批完成，
  且有效校验（有复核以复核为准，无复核以报名时校验为准）通过的报名；
  退回未重提、驳回、能力校验失败、复核失败的已批人员都不计数。

### 关键接口
- `PUT /api/positions/{id}`：门槛改动触发同事务重检；返回体带 `occupiedCount`、`full`、`requirementVersion`。
- `GET /api/positions/{id}/occupied`：查询岗位实际占编人数 / 是否满员。
- `POST /api/registrations/{id}/resubmit`：退回单重新送审（body 可带 `{ "applyMessage": "..." }`）。

