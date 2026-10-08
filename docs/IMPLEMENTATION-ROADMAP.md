# 从骨架到毕业设计的开发顺序

当前已完成入社、活动发布/报名与站内通知三条业务，分别见 `RECRUITMENT-WORKFLOW.md`、`ACTIVITY-WORKFLOW.md`、`NOTIFICATION-WORKFLOW.md`。每条跨页面验收，不要同时填写所有模块。

| 顺序 | 要补齐的内容 | 后端起点 | 前端起点 | 验收方式 |
|---|---|---|---|---|
| 1 | Session 登录与所属社团校验已实现；社团维护待做 | `identity`、`club` | `LoginView.vue` | 已验证登录与管理范围；不宣称维护已完成 |
| 2 | 入社申请、审核、撤回和成员关系已实现；招新条件维护待做 | `recruitment`、`membership` | `BusinessPersonalView.vue`、`BusinessManageView.vue` | 已验证审核与成员事务、重复/越权拦截及重启保留 |
| 3 | 活动草稿、发布、报名/取消及名单已实现；编辑、下架和签到待做 | `activity`、`registration` | `community/Community*Activit*.vue` | 已验证名额/截止/权限、取消释放及同 ID 重启保留，见活动验证记录 |
| 4 | 站内通知已实现；实时推送未做 | `notification` | `community/CommunityMessagesView.vue` | 业务与通知同事务、本人分页与已读、结果跳转及重启保留，见通知验证记录 |
| 5 | 兴趣推荐 | 校园服务新增 `profile`；AI 服务新增 `recommendation` | 社团页增加推荐入口 | 对比关键词、语义、规则融合方案，不出现明确资格违规 |
| 6 | 资料权限与正式持久化 | 扩展 AI 文档范围；完善 MySQL 迁移 | 知识库权限界面 | 跨社团资料不能被检索，重启保留问答及出处 |
| 7 | 策划助手与人工确认 | AI 服务新增 `planning`；校园服务保存正式活动 | 新增草稿编辑与确认页 | 未确认不发布，事实来自业务查询 |
| 8 | ReAct / MCP / DAG | 按实际工具需求逐项新增 | 工具状态与结果展示 | 每项有独立用例与运行记录 |

每次建议先写该模块的输入、状态和失败情况，再实现一条页面—接口—数据链。采用 Controller → Service → Repository；活动规则现已由 ActivityService 处理，社团基础查询仍直接使用 Repository，后续按实际需求扩展。

入社身份已接通，但 AI 会话与资料权限仍未按用户/社团隔离，继续只使用公开模拟资料。下一步引入 AI 权限时，将会话绑定用户，检索绑定可见社团范围，不能把入社登录误当成 AI 全链路认证。

论文和开题仍按校园业务需求、服务边界、推荐方法及验证结果组织；代码中的来源与许可证说明保留在工程文件中。未完成部分写“计划实现”，测试记录只写实际执行结果。
