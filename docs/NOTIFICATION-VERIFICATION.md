# 站内通知业务验证记录

2026-10-05。本次只实现第三条通知链，没有扩展 AI、签到、经费审批或公网服务。

## 自动化与构建

- `mvn -DskipTests=false test`、`mvn -DskipTests=false package`：通过。校园服务 25 个测试、AI 服务原有 10 个测试，共 35 个，无失败、错误或跳过。
- 新增 NotificationApiTest 6 个用例：四种通知、真实重新报名与重复请求区别、账号分页隔离、Session/CSRF、单条/全部已读幂等、业务及通知事务回滚、并发重复报名只产生一条通知。
- 事务失败测试通过注入通知仓库写入异常验证：审核状态仍 pending、未新增成员、报名不占位、取消失败后仍保留有效报名。使用独立 H2 内存库与 MockMvc，不是破坏真实文件库制造故障。
- `npm run test:business`：Mock HTTP + SSR，通过通知列表、真实模式无模拟消息、铃铛计数、单条/全部已读、失败不伪装成功、账号切换立即隐藏旧消息及迟到响应保护。不是实际浏览器证据。
- `npm run test:prototype`：80 条断言、22 个路由；纯原型活动与通知模块不调用业务 API。原型归档没有改动。
- `npm run build`：Vue/TypeScript 与 Vite 构建通过。公共纯前端配置构建通过，没有发布公网。

## 真实 HTTP 与重启

`node scripts/check-notifications.mjs` 通过。本次新建 activityId=65，名称“站内通知联调（虚构验收活动）”；student 报名/取消/重新报名产生 notificationId=1、2、3；重复报名和重复取消均返回 409，没有额外通知。student2 的通知不出现在 student 列表中，跨账号已读返回 404。

单条已读重复提交保留相同时间；student2 全部已读不影响 student 未读数。记录保存在 `.run/notification-smoke.json`。这是本地虚构数据，不是学校真实活动；验收活动与通知保留，没有删除旧业务记录。

停止并重新启动本项目三个进程后，`node scripts/check-notifications.mjs restored` 通过：读取的仍为 notificationId=1、2、3，原 readAt=`2026-10-05T12:42:43.204366Z` 保留，其他两条仍未读。不是重造相同内容来冒充持久化。

同时执行旧业务 restored 检查：入社申请和成员记录保留；activityId=33、registrationId=1 保留。通用 `scripts/smoke.mjs` 通过，资料问答为 LOCAL 模式；不代表真实模型或 Redis 实例验收。

## 实际浏览器

1. 未登录打开 `/messages`，显示登录提示，没有学生模拟消息。
2. 登录 student，看到 3 条本人消息、2 条未读；点击已读的报名成功消息，进入 activityId=65 并显示当前已报名状态。
3. 重启后登录 student2，在同一虚构验收活动取消并重新报名；有效人数 2→1→2，未读计数 0→1→2。没有操作旧活动 33/34 的报名。
4. student2 消息中心显示其自身 3 条通知；单条已读后未读 2→1，全部已读后 1→0，铃铛徽标消失。没有改变 student 的两条未读消息。
5. 390×844、375×812、812×375、1440×1000 视口无横向溢出；手机保留六个底部入口。新消息按钮实际高度 44px，使用文字区分已读/未读。没有新增动画或远程图片。

截图在 `docs/screenshots/notifications-2026-10-05/`：`mobile-unread.jpg`、`mobile-read.jpg`、`desktop-read.jpg`。临时视口覆盖已恢复，预览留在 `/messages`，当前为 student2 虚构账号。只是本地浏览器尺寸测试，不宣称实体手机或微信小程序验收。

## 边界

审核通过/拒绝的通知本次由后端自动化验证，未另在真实浏览器新增入社审核操作。分页的多页数据由独立内存库验证，真实页面本次仅 3 条本人通知。没有吞吐压测、生产迁移、实时推送、短信/微信通知、独立消息服务或 Kafka。MySQL/Redis 真实实例及完整 AI 数据权限仍未在本次验证。

默认使用 H2 文件库；所有三个运行服务只监听 loopback。正式上线仍需完成原有安全与部署要求，不能因通知权限完成就开放公网。
