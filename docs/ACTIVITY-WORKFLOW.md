# 第二条业务：活动发布与报名

## 固定案例与使用入口

摄影社负责人填写“校园光影漫步”，地点为“学生中心 204”，容量 30 人。保存得到草稿，此时学生不可见；负责人确认发布后，学生从活动列表进入详情，确认报名。报名人数由数据库实时统计，个人中心显示已报名；活动开始前可取消并释放名额。负责人只能查看自己社团的有效报名名单。

- 本机入口：`http://127.0.0.1:5178/activities`。
- 负责人：`photo_manager`，管理入口 `/manage/activities`。学生：`student` 或 `student2`。虚构演示密码 `CampusDemo123!`。
- 所有账号、社团和活动资料均为本地测试数据；不是已部署的正式校园平台。不要开公网。

## 本次范围与明确选择

面向所有学生，不以社团成员关系作为报名条件。活动地点直接填文本，不新建场地资源库、空闲查询或预约系统。时间按校园本地时间填写，开发机为 Asia/Hong_Kong；截止时间可以等于开始时间，但必须在保存、发布时仍为未来时间。开始前可取消，即使报名截止已过；截止后不能重新报名。

本次未实现：活动草稿编辑、活动下架、活动取消、签到、经费审批、推送通知、场地资源维护及 AI 策划。现在的人工确认是普通界面的写入确认，不宣称已经接入 Agent HITL。

## 一次请求怎样走

学生点击“确认报名” → Vue `registerActivity` → `request` 获取最新 CSRF 令牌并发送 Session Cookie → `POST /api/activities/{id}/registrations` → `ActivityService.register` 从当前 Session 查学生 → 锁定该活动 → 校验已发布、未截止、没有有效报名、仍有名额 → 保存 `activity_registration` → 事务提交 → 前端重新查询真实人数与自己的报名记录。

取消沿用相同活动锁，校验本人和开始时间，记录状态改为 CANCELLED。不会物理删除记录。负责人发布时校验数据库中的所属社团关系，不接受前端假身份或 userId。

## 数据、状态和并发

- `club_activity`：保留原 SAMPLE 示例行及 ID，新增 description、registrationDeadline、createdBy、publishedAt。新活动为 DRAFT，确认后 PUBLISHED；公开接口不返回草稿与旧 SAMPLE。
- `activity_registration`：活动 ID、用户 ID、REGISTERED/CANCELLED、报名/取消时间。用户—活动唯一约束；重新报名激活同一记录，不制造多份有效报名。
- 人数为 `REGISTERED` 的数据库计数。报名和取消在一个数据库事务中锁定同一活动行，最后一个名额不超卖；不依赖 Redis、AI 或 Kafka。
- 取消后重新报名会更新最近报名时间，保留的是当前状态及最近状态时间，不是完整多次操作审计历史。完整审计日志仍待实现。
- 目前按活动串行处理报名，适用于毕设的小规模演示；没有做吞吐压测、列表分页或正式 MySQL 实例验证，不宣称高并发指标。

## 界面设计与源码入口

延续用户确认的 `COMMUNITY-DESIGN.md`：白底、绿色操作强调、系统中文字体、左对齐紧凑列表，不使用大幅营销标题或装饰图片。活动列表用日期块辅助识别；详情用时间、地点、截止和名额事实；管理页用有标签的原生表单和独立发布确认。手机单列，电脑沿用社群导航外壳。其他原型页面不做全站换肤。

frontend-design 用于内容层级与克制文案；ui-ux-pro-max 用于表单错误、操作反馈、焦点和手机点击区检查。本次复用既有设计体系，未采用新配色模板。

- 后端：`activity/ActivityController.java` → `ActivityService.java` → `ActivityRepository.java`、`registration/RegistrationRepository.java`。
- 前端：`community/activities.ts`、`CommunityActivitiesView.vue`、`CommunityActivityDetail.vue`、`CommunityManageActivities.vue`、`CommunityDialog.vue`。
- “我的”增加数据库活动报名标签；首页与社团详情的活动入口在正常模式下读取真实已发布活动，不混用原型 ID。
- `VITE_BUSINESS_API=false` 的原型与公共构建继续使用原有浏览器模拟流程，不调用业务 API。原型归档保持不动。

## 验收入口

自动化：`mvn -DskipTests=false test`、前端 `npm run build`、`npm run test:prototype`、`npm run test:business`。

运行三个本地服务后：`node scripts/check-activities.mjs` 检查真实发布、报名、取消、重报、重复/满员、CSRF、权限与重新登录，保留本机虚构验收记录。重启项目后运行 `node scripts/check-activities.mjs restored`，只核对同一个已保存活动及报名 ID。旧入社链使用 `node scripts/check-recruitment.mjs restored` 回归。实际执行记录另见 `ACTIVITY-VERIFICATION.md`，不得把这里的验收目标当成已通过结果。
