# 活动发布与报名验证记录

日期：2026-10-05。仅本机开发环境与虚构 demo 账号；默认 H2 文件库。没有启用公网、真实学校账号、MySQL 实例或外部付费模型。

## 自动化检查

- `mvn -DskipTests=false test`、`mvn -DskipTests=false package`：通过。校园服务 19 个测试（活动 7、原入社 9、基础接口 3），AI 服务 10 个测试，失败/错误/跳过均为 0。
- 活动测试覆盖草稿不公开、确认发布、重复发布、身份/CSRF、跨社团权限、学生报名/取消/重报、名额满、取消释放、时间错误、截止及已开始、数据库保存失败回滚、两人抢最后名额、同一学生并发防重。测试的 AI 地址设置为不可用端口，业务流程仍通过；这是测试环境故障隔离证明，不是本次额外进行的实际 AI 进程停机实验。
- `npm run build`：Vue/TypeScript 与 Vite 构建通过。
- `npm run test:prototype`：78 条断言、22 个 SSR 路由通过；新增检查纯原型的活动 store 不发起业务 API 请求。
- `npm run test:business`：通过。Mock HTTP + SSR 验证活动接口封装、CSRF、草稿/发布、报名/取消、身份切换立即隐藏旧记录、失败不回退到假数据，以及原入社链回归。不是实际浏览器操作或真实 HTTP 后端。
- 公共纯原型 Vite 构建：通过；没有启动公网服务。

## 真实本地 HTTP 与持久化

启动三个本项目进程后，`node scripts/check-activities.mjs` 通过：保留 activityId=33、registrationId=1 的虚构活动/报名；校验重复、满员、取消/重报、真实人数、越权、CSRF、重新登录。验收结束时 student 保留该活动报名，student2 对该活动为已取消。

`node scripts/smoke.mjs` 通过：社团、已发布活动、公开测试文本上传及本地关键词问答连通。最终存在两场已发布演示活动。当地模式问答仍是原文检索摘录，不宣称本次调用了大模型或 Embedding。

随后通过本项目 `stop-dev.ps1`、`start-dev.ps1` 停止并重新启动登记的三个进程，未删除数据库。`node scripts/check-activities.mjs restored` 通过：同一个 activityId=33、registrationId=1 的已发布及已报名状态仍在。`node scripts/check-recruitment.mjs restored` 也通过，原入社申请和成员关系保留。

## 实际浏览器操作

1. photo_manager 登录，打开活动管理，填写“校园光影漫步（演示）”、学生中心 204、2026-10-17 14:00 开始、2026-10-16 18:00 截止、30 人。保存出现 activityId=34 草稿，状态明确为学生不可见；点击确认发布，弹窗核对后才提交发布。
2. student2 登录，从活动列表进入 activityId=34；确认报名后人数由 0/30 变 1/30；确认取消后变 0/30；重新报名恢复 1/30。
3. 个人中心活动报名标签显示 activityId=34 已报名、activityId=33 已取消。“我的报名”链接已直达该标签，不再默认打开入社申请。
4. 项目重启后重新登录 photo_manager，名单读取到陈同学的有效报名；再次登录 student2 后，同一活动显示已报名并可取消。
5. 检查 375×812 的管理表单与名单、390×844 的列表和详情、1440×1000 的管理页面、812×375 横屏列表；页面没有横向溢出。临时视口覆盖已恢复。没有宣称已在实体手机、微信小程序或多浏览器完成验收。

截图在 `docs/screenshots/activities-2026-10-05/`：`list-mobile.jpg`、`detail-mobile.jpg`、`manager-mobile.jpg`、`manager-desktop.jpg`。一次详情全页截图失败，重试普通视口截图成功，保存的是实际浏览器结果。

结束时三个服务只监听 127.0.0.1 的 5178/8090/8091，预览页保留在 `/activities`，浏览器使用 student2 虚构账号。新增验收资料保留供继续体验，没有物理删除活动或报名。

## 尚未验证或实现

MySQL/Redis 真实实例、容量吞吐压测、跨时区部署、正式迁移、账号注册、活动编辑/下架/签到、通知、完整审计历史、AI 策划与 Agent HITL 尚未实现或验证。当前实现的是有限规则下可演示的数据库业务链，不代表完整生产系统。
