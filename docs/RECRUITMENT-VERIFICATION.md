# 入社业务验证记录

日期：2026-10-04，Asia/Hong_Kong。该记录只覆盖第一条入社业务，不代表整个毕设完成。

## 已完成检查

- 根项目 `mvn -DskipTests=false package`：校园服务 12 个用例（原有 3、新增 9），AI 服务 10 个回归用例，均无失败/错误/跳过。
- 新增测试：真实 Session 登录、错误密码、CSRF 拦截、学生身份与记录隔离、负责人所属社团校验、理由/意见参数校验、招新结束、重复申请、重复审核、撤回/拒绝后重新申请和历史保留。
- 数据完整性：模拟成员写入失败，申请仍为 pending；同时提交两份申请仅一份成功；同时通过与拒绝同一申请仅一个决定成功，成员与最终状态一致。
- 前端 `npm run build`：类型检查与生产构建通过。
- 纯原型 `npm run test:prototype`：67 项断言、22 条路由通过；保留浏览器模拟模式。
- 真实业务前端 `npm run test:business`：模拟 HTTP + SSR 检查 CSRF、切换账号清空私有记录、申请/审核/成员展示、后端失败不回落假数据。这不是实际浏览器测试。
- 实际 HTTP：`check-recruitment.mjs` 经 5178 代理验证申请、审核、成员、401/403/409 与重新登录；验收记录在 `.run/recruitment-smoke.json`，仅使用虚构账号。
- 原有资料回归：`smoke.mjs` 验证社团/活动查询、模拟 Markdown 上传与防重复、关键词问答、出处与历史；未调用真实付费模型。
- 实际浏览器：student2 登录、填写申请、看到待审核；photo_manager 登录通过并查看陈同学出现在成员名单；重新以 student2 登录，看到已加入摄影社。
- 手机宽度：390×844 的浏览器模拟视口检查，负责人/学生页 document.scrollWidth=375≤390，六个底部菜单正常显示。不是实体手机测试。

截图目录：`screenshots/recruitment-2026-10-04/`，包含 `manager-members.jpg`、`student-membership-mobile.jpg` 和重启后记录仍保留的 `student-membership-after-restart.jpg`。

## 重启与故障隔离

本节以实际重跑后的记录为准，不以单元测试或源码替代进程验证。

- 已停止并重新启动本项目三个进程，执行 `check-recruitment.mjs restored`，同一 applicationId=1 的 approved 申请、学生成员关系、负责人名单均保留；不是新建申请替代旧记录。
- `check-resilience.mjs restored` 通过：先前上传资料、会话及引用同样保留。
- 仅关闭本项目 AI 服务，`check-resilience.mjs down` 验证 AI 返回 503，社团/活动查询仍为 200；入社验收脚本继续通过。
- AI 关闭期间，以 student2 向程序设计协会提交了一份新申请，code_manager 审核通过；新的申请状态和成员关系均保存成功。此检查覆盖真正的新写操作，而不只是已有记录查询。
- AI 服务随后已恢复；没有修改或重启原 PaiSmart 服务。公网通道仍关闭。

## 数据保护与限制

首次启动新业务前将旧校园 H2 文件备份到 `.run/before-recruitment-2026-10-04/campus.mv.db`。旧示例社团保留原 ID 和原名称，新增招新字段及剩余社团；没有清空数据库。原 PaiSmart、PaiCLI 与 2026-10-01 原型存档未编辑。

默认开发库仍为 H2，MySQL/Redis 实例未在本次联调；没有高负载压测或真实校园用户实验。账号注册、学校身份、登录限流、社团资料维护、活动报名、正式通知、AI 资料隔离、推荐及 ReAct/MCP/DAG 仍待实现。
