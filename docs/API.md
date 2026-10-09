# 当前接口

前端和联调统一使用校园服务的 `/api`。AI 服务的 `/internal` 接口只用于内部开发通信，正式部署需增加网络与服务认证。

| 方法 | 校园服务路径 | 当前行为 |
|---|---|---|
| GET | `/api/system` | 已实现/示例/待实现模块状态 |
| GET | `/api/clubs` | 数据库社团列表（含公开虚构演示资料） |
| GET | `/api/clubs/{id}` | 社团详情，不存在返回 404 |
| GET | `/api/auth/session` | 当前身份、CSRF token/header 名称、是否启用演示账号；匿名也可读取 |
| POST | `/api/auth/login` | form-urlencoded 的 username/password；成功建立 HttpOnly Session Cookie |
| POST | `/api/auth/logout` | 注销当前会话，业务记录不删除 |
| POST | `/api/auth/register` | JSON 学生注册；匿名可用，但仍需 CSRF；不能申请负责人角色 |
| GET | `/api/profile` | 当前账号资料与兴趣，不接受他人 userId |
| PUT | `/api/profile` | 修改本人姓名、专业、兴趣数组和空闲时间文字，不改角色/账号 |
| GET | `/api/manage/clubs` | 当前账号负责的社团 |
| POST | `/api/manage/clubs` | 已有负责人创建社团，同时写入该负责人关系；学生不可创建 |
| PUT | `/api/manage/clubs/{id}` | 所属负责人维护社团与招新资料 |
| GET | `/api/favorites` | 当前账号的社团收藏 |
| PUT | `/api/favorites/{clubId}` | 收藏社团；重复操作返回同一记录和创建时间 |
| DELETE | `/api/favorites/{clubId}` | 删除本人收藏，返回 changed=0/1；他人收藏不变 |
| POST | `/api/recruitment/applications` | 学生提交 clubId/reason；身份从 Session 读取 |
| GET | `/api/recruitment/my-applications` | 当前账号的申请记录，不返回其他学生申请 |
| POST | `/api/recruitment/applications/{id}/withdraw` | 仅本人可撤回待审核申请 |
| GET | `/api/memberships/mine` | 当前账号数据库成员关系 |
| GET | `/api/manage/clubs/{clubId}/applications` | 负责人查看本社团申请 |
| GET | `/api/manage/clubs/{clubId}/members` | 负责人查看本社团成员，含负责人 |
| POST | `/api/manage/applications/{id}/review` | approved 为必填布尔值，feedback 可选；校验该申请所属社团权限 |
| GET | `/api/activities` | 仅已发布活动及实时有效报名人数；匿名可读，草稿和 SAMPLE 不公开 |
| GET | `/api/activities/{id}` | 已发布或曾发布后取消的活动详情；未发布或不存在返回 404 |
| GET | `/api/manage/clubs/{clubId}/activities` | 负责人查看本社团草稿、已发布及已取消活动（不含 SAMPLE） |
| POST | `/api/manage/clubs/{clubId}/activities` | 保存活动草稿，校验本社团权限、必填项及时间 |
| POST | `/api/manage/activities/{id}/publish` | 仅本社团负责人确认发布草稿；重复发布返回 409 |
| PUT | `/api/manage/activities/{id}` | 编辑未开始的草稿/已发布活动，输入与草稿相同；有人报名后不可改时间/地点 |
| POST | `/api/manage/activities/{id}/cancel` | 负责人取消未开始、无人签到的活动；保留记录、同步取消有效报名并通知报名者 |
| GET | `/api/manage/activities/{id}/check-in` | 所属负责人读取签到开关、码、报名和签到人数 |
| POST | `/api/manage/activities/{id}/check-in/open` | 已发布活动开始前 30 分钟起可开启；已经开启时返回原码 |
| POST | `/api/manage/activities/{id}/check-in/close` | 所属负责人关闭签到并清除当前码 |
| POST | `/api/activities/{id}/check-in` | 已报名学生提交 code 签到；当前开关/码有效时重复签到返回原时间 |
| POST | `/api/activities/{id}/registrations` | 当前学生账号报名；校验截止、状态、重复、容量 |
| POST | `/api/activities/{id}/registrations/cancel` | 当前学生取消自己的报名；开始后或已签到不能取消 |
| GET | `/api/registrations/mine` | 当前账号报名及取消记录，不接收 userId 参数 |
| GET | `/api/manage/activities/{id}/registrations` | 本社团负责人读取当前有效报名名单，不公开给其他学生或社团 |
| GET | `/api/manage/activities/{id}/finance` | 所属负责人读取预算、支出合计、余额、超预算标记及保留的全部支出记录 |
| GET | `/api/manage/clubs/{clubId}/finance` | 所属负责人读取社团预算/支出汇总与未设预算活动数 |
| PUT | `/api/manage/activities/{id}/budget` | 设置非负预算 amount，最多两位小数；不执行支付或审批 |
| POST | `/api/manage/activities/{id}/expenses` | 支出录入；同一活动+requestId+相同内容返回同一记录，不重复记账 |
| POST | `/api/manage/expenses/{id}/void` | 所属负责人作废误记支出并保存原因/操作人/时间，重复作废返回 409 |
| GET | `/api/notifications?page=0` | 当前账号消息，每页 20 条，返回 items/total/page/hasMore/unread；未读数覆盖全部页 |
| POST | `/api/notifications/{id}/read` | 本人消息标为已读，重复操作保留原 readAt；他人或不存在消息统一 404 |
| POST | `/api/notifications/read-all` | 当前账号全部未读消息标为已读，返回 changed 数量；不影响其他账号 |
| GET | `/api/ai/status` | AI 模式、资料数、会话存储模式 |
| GET | `/api/ai/knowledge` | 已上传资料列表 |
| GET | `/api/ai/knowledge/{id}` | 资料元数据与全文 |
| POST | `/api/ai/knowledge` | multipart 字段 `file` 上传公开文本 |
| POST | `/api/ai/chat` | 资料问答，保存回答与引用 |
| GET | `/api/ai/conversations/{id}` | 会话历史与引用 |

问答请求：

```json
{"question":"程序设计社适合零基础吗？","conversationId":null}
```

首次响应包含服务生成的 UUID `conversationId`，后续请求可传这个 ID。`mode=LOCAL` 表示原文摘录；`mode=OPENAI` 表示兼容模型接口；`mode=OLLAMA` 表示原生 Ollama 模型接口。模式是配置，不是健康检查；无检索依据时不调用聊天模型，直接提示资料不足。`references` 包含资料 ID、名称、片段号、摘录和检索得分，`retrieval` 表示本次实际采用的检索方式。引用是检索原文，不代表每个生成结论均已核验。公开测试范围和验收见 [RAG 问答说明](RAG-WORKFLOW.md)。

写请求需先 GET `/api/auth/session`，将 csrfToken 放入 csrfHeader 指定的请求头，同时保留 Session Cookie。登录、退出会轮换 CSRF 状态，下一次写请求重新取 token。前端 `request()` 已封装这一过程。

入社请求示例：`{"clubId":2,"reason":"希望学习摄影"}`；clubId 应从社团查询获取，不硬编码。审核示例：`{"approved":true,"feedback":"欢迎加入"}`。理由为 1～500 字，审核意见最多 300 字。

申请状态为 pending/approved/rejected/withdrawn。被拒或撤回后可新建申请，旧记录保留；已有待审核申请或已入社返回 409。审核与成员写入在同一事务中，重复审核返回 409。未登录返回 401，CSRF 错误或越权返回 403，记录不存在返回 404。

活动草稿请求：`{"title":"手机摄影交流","description":"校园构图练习","location":"学生中心 204","startTime":"2026-10-17T14:00:00","registrationDeadline":"2026-10-16T18:00:00","capacity":30}`。名称最多 100 字、介绍最多 2000 字、地点最多 200 字、容量 1—500 人。时间为校园本地时间（当前本机 Asia/Hong_Kong）；截止须在未来且不晚于开始。草稿保存后还需单独发布，不存在自动发布行为。报名无需先加入社团；负责人账号不能报名。取消保留记录，重新报名复用同一用户—活动记录，再次校验截止及容量。

站内通知由审核通过/拒绝、报名成功、取消成功自动产生，不提供客户端创建通知接口。业务与通知在同一事务中写入；重复/失败请求不产生通知，取消后重新报名是新事件。通知保存发生时的正文和时间，查看相关记录时显示当前业务状态。不补造接入前历史通知，不代表微信推送或实时消息。读取与已读操作只从 Session 获取账号身份。

错误采用 HTTP 状态码及 `detail` 文本。AI 服务不可用时返回 503，校园注册、社团、招新、活动、签到、经费、收藏与通知业务不调用 AI。AI 开发接口目前仍为公开测试资料，仅限本地环境；新增业务权限不代表 AI 数据权限已经完成。

## 本轮新增请求与字段

注册：`{"username":"campus_student","password":"填写8至72字符密码","name":"学生姓名","major":"软件工程"}`。账号只接受 3—32 位小写字母、数字、下划线；密码还需不超过 UTF-8 72 字节。成功后另行登录；响应不含密码或密码散列。用户名冲突返回 409。

资料：`{"name":"学生姓名","major":"软件工程","interests":["摄影","编程"],"availableTime":"周三晚上"}`。兴趣最多 12 个、每个最多 20 字，去首尾空白、去重且单个标签不能含逗号。空闲时间最多 200 字，是文字资料，不是排课或冲突计算结果。

社团：`{"name":"摄影社","category":"艺术","description":"手机摄影交流","tags":"摄影,构图","campus":"主校区","recruiting":true,"requirements":"欢迎零基础学生","schedule":"周三晚上","place":"学生中心"}`。name/category/description/campus/requirements/schedule/place 必填；tags 可为空但不能为 null，recruiting 为必填布尔值。字段上限见 `ClubManagementController.ClubRequest`。

活动取消：`{"reason":"负责人填写取消原因"}`，最多 300 字。不删除活动/报名记录；有效报名转 CANCELLED，并与 ACTIVITY_WITHDRAWN 通知在同一事务内保存。未发布的取消草稿仍不公开。活动列表仍只显示 PUBLISHED，通知对应的曾发布已取消活动可通过详情读取。

公开活动响应增加 updatedAt/updatedBy、cancelledAt/cancelledBy/cancelReason、checkInOpen；**不返回签到码或财务信息**。本人报名记录和负责人报名名单增加 checkedInAt。学生签到输入 `{"code":"现场负责人提供的码"}`；目前没有微信二维码、定位或自动截止，负责人需手动关闭。关闭再开启生成新码；码校验通过且开启时重复签到保留原 checkedInAt。

预算：`{"amount":"100.00"}`。支出：`{"requestId":"客户端为该笔支出生成的UUID","amount":"18.50","description":"材料费","occurredOn":"2026-10-05"}`，occurredOn 不能在未来，重试同一笔必须复用 UUID；改变内容时不能复用旧 UUID。金额最多 8 位整数、2 位小数，支出至少 0.01。作废：`{"reason":"误录作废"}`，最多 300 字。

经费只汇总 ACTIVE 支出，VOID 记录仍完整返回但不计入合计。未设置预算时 budget/remaining 为 null，overBudget=false（表示没有可比较预算，不是预算足够）；设置预算后 remaining=budget-spent，允许负数并标记 overBudget=true。取消活动仍保留预算和支出，可记录实际已发生费用；SAMPLE 占位活动不能记账。财务与签到码接口不对学生或其他社团负责人开放。

所有新增功能本轮仅提供后端 API，对应前端页面尚未接入。
