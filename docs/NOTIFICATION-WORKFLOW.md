# 第三条业务：结果通知与消息中心

业务成功 → 同一事务写入站内通知 → 学生查看消息 → 查看相关记录或标为已读。

## 范围

仅通知入社审核通过、审核拒绝、活动报名成功、取消报名成功。没有消息队列、WebSocket、短信或微信推送；页面进入、业务操作完成及手动刷新时获取消息。旧业务记录不回填通知。所有本地账号与验收活动均为虚构数据。

消息正文保存发生时的结果，不随后续取消/重新报名重写；点击相关记录查看当前状态。因此一条“报名成功”历史消息仍会保留，即使此后取消。纯原型仍使用浏览器模拟消息，正常模式读取校园数据库。

## 一次完整操作

学生报名活动 → ActivityService 从 Session 确认身份 → 锁定活动并校验状态/截止/容量/重复 → 保存报名 → NotificationService 保存本人通知 → 事务提交。任一步写入失败，一起回滚；不会留下成功通知或悄悄占用名额。

取消后可重新报名，每次有效状态变化都有新消息；同一状态下重复请求返回 409，不产生额外通知。入社审核同理，申请行锁与待审核校验阻止重复审核；通过时成员、审核结果、通知同事务提交。当前没有异步消费或自动事件重放，因此没有引入额外去重系统。

## 数据与权限

`station_notification` 保存 id、userId、type、sourceId、title、content、targetPath、createdAt、readAt。readAt 为 null 表示未读。接收人及内部跳转路径由后端产生，客户端不能指定账号或创建消息。

列表按 id 倒序，每页 20 条，未读计数为该账号所有页总和。单条已读为条件更新，重复操作保留原时间；全部已读只更新当前账号未读行。他人消息与不存在消息均返回 404，不泄露其是否存在。POST 沿用 Session 与 CSRF。

## 阅读源码的顺序

1. `activity/ActivityService.java` 的 register/cancel；`recruitment/RecruitmentService.java` 的 review：通知何时被业务触发。
2. `notification/NotificationService.java`：send 必须加入已有事务；inbox/read/readAll 以当前账号约束查询与更新。
3. `notification/NotificationRepository.java`：分页与只更新未读行。
4. `frontend/src/community/notifications.ts`：请求封装、账号切换与迟到响应保护。
5. `CommunityMessagesView.vue`：消息列表、已读按钮、分页、查看相关记录。

入社通知跳转 `/me?tab=applications`，活动通知跳转 `/activities/{id}`。点击查看先确认已读写入成功，再跳转；写入失败保留错误提示。前端读取失败不回退成模拟通知。

## 验收与运行

后端：`mvn -DskipTests=false test`。前端：`npm run build`、`npm run test:business`、`npm run test:prototype`。

启动本地服务后执行 `node scripts/check-notifications.mjs`，它新建明确命名的虚构验收活动并保留通知；只在该活动报名/取消/重新报名，不删除旧记录。全部已读测试只影响 student2 演示账号。重启后执行 `node scripts/check-notifications.mjs restored`，读取保存的通知 ID 和原 readAt，验证数据没有被重新生成。

实际执行证据见 `NOTIFICATION-VERIFICATION.md`；H2 文件库为当前持久化方式，不宣称已迁移 MySQL 或完成学校正式部署。
