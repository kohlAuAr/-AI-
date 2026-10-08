# 从骨架到毕业设计的开发顺序

当前入社、活动发布/报名、站内通知、首页轮播管理以及注册/个人资料已接入页面；社团维护、活动编辑/取消/签到、经费和账号收藏已具备后端，前端仍待补齐。分别见 `RECRUITMENT-WORKFLOW.md`、`ACTIVITY-WORKFLOW.md`、`NOTIFICATION-WORKFLOW.md`、`PLATFORM-BANNERS.md` 和 `ACCOUNT-PROFILE-WORKFLOW.md`。后续仍按一条业务从页面到接口逐项验收，不把菜单数量当作完成度。

| 顺序 | 要补齐的内容 | 后端起点 | 前端起点 | 验收方式 |
|---|---|---|---|---|
| 1 | 学生注册/资料已接页面；下一步补负责人社团维护页面 | `identity`、`club` | 登录、个人页及管理页 | 注册只能学生；资料只能本人；社团只能所属负责人维护 |
| 2 | 招新与成员链已接入页面；招新条件维护 API 已实现，补编辑页面 | `recruitment`、`membership`、`club` | `BusinessPersonalView.vue`、`BusinessManageView.vue` | 审核与成员事务、重复/越权拦截、招新开关及重启保留 |
| 3 | 活动草稿/发布/报名已接页面；编辑/取消/签到后端已实现，补页面 | `activity`、`registration` | `community/Community*Activit*.vue` | 有人报名不改时地、取消通知、码权限、签到防重与数据保留 |
| 4 | 站内通知已实现；实时推送未做 | `notification` | `community/CommunityMessagesView.vue` | 业务与通知同事务、本人分页与已读、结果跳转及重启保留，见通知验证记录 |
| 5 | 经费和账号收藏 API 已实现，补页面对接 | `finance`、`favorite` | 管理端经费页、收藏页 | 金额精度、同笔防重、作废留痕、跨账号隔离及重启保留 |
| 6 | 兴趣推荐 | 校园服务已有 `identity/ProfileService`；AI 服务新增 `recommendation` | 社团页增加推荐入口 | 对比关键词、语义、规则融合方案，不出现明确资格违规 |
| 7 | 资料权限与正式持久化 | 扩展 AI 文档范围；完善 MySQL 迁移 | 知识库权限界面 | 跨社团资料不能被检索，重启保留问答及出处 |
| 8 | 策划助手与人工确认 | AI 服务新增 `planning`；校园服务保存正式活动 | 草稿编辑与确认页 | 未确认不发布，事实来自业务查询 |
| 9 | ReAct / MCP / DAG | 按实际工具需求逐项新增 | 工具状态与结果展示 | 每项有独立用例与运行记录 |

每次建议先写该模块的输入、状态和失败情况，再实现一条页面—接口—数据链。采用 Controller → Service → Repository；活动规则由 ActivityService 处理，社团维护由 ClubManagementService 处理，基础查询仍直接使用 Repository。校园后端范围已具备，但不等于所有新功能已有可操作页面，也不等于可公开生产部署。

入社身份已接通，但 AI 会话与资料权限仍未按用户/社团隔离，继续只使用公开模拟资料。下一步引入 AI 权限时，将会话绑定用户，检索绑定可见社团范围，不能把入社登录误当成 AI 全链路认证。

论文和开题仍按校园业务需求、服务边界、推荐方法及验证结果组织；代码中的来源与许可证说明保留在工程文件中。未完成部分写“计划实现”，测试记录只写实际执行结果。
