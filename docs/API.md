# 当前接口

前端和联调统一使用校园服务的 `/api`。AI 服务的 `/internal` 接口只用于内部开发通信，正式部署需增加网络与服务认证。

| 方法 | 校园服务路径 | 当前行为 |
|---|---|---|
| GET | `/api/system` | 已实现/示例/待实现模块状态 |
| GET | `/api/clubs` | 示例社团列表 |
| GET | `/api/clubs/{id}` | 社团详情，不存在返回 404 |
| GET | `/api/auth/session` | 当前身份、CSRF token/header 名称、是否启用演示账号；匿名也可读取 |
| POST | `/api/auth/login` | form-urlencoded 的 username/password；成功建立 HttpOnly Session Cookie |
| POST | `/api/auth/logout` | 注销当前会话，业务记录不删除 |
| POST | `/api/recruitment/applications` | 学生提交 clubId/reason；身份从 Session 读取 |
| GET | `/api/recruitment/my-applications` | 当前账号的申请记录，不返回其他学生申请 |
| POST | `/api/recruitment/applications/{id}/withdraw` | 仅本人可撤回待审核申请 |
| GET | `/api/memberships/mine` | 当前账号数据库成员关系 |
| GET | `/api/manage/clubs/{clubId}/applications` | 负责人查看本社团申请 |
| GET | `/api/manage/clubs/{clubId}/members` | 负责人查看本社团成员，含负责人 |
| POST | `/api/manage/applications/{id}/review` | approved 为必填布尔值，feedback 可选；校验该申请所属社团权限 |
| GET | `/api/activities` | 仅已发布活动及实时有效报名人数；匿名可读，草稿和 SAMPLE 不公开 |
| GET | `/api/activities/{id}` | 已发布活动详情；未发布或不存在返回 404 |
| GET | `/api/manage/clubs/{clubId}/activities` | 负责人查看本社团草稿与已发布活动 |
| POST | `/api/manage/clubs/{clubId}/activities` | 保存活动草稿，校验本社团权限、必填项及时间 |
| POST | `/api/manage/activities/{id}/publish` | 仅本社团负责人确认发布草稿；重复发布返回 409 |
| POST | `/api/activities/{id}/registrations` | 当前学生账号报名；校验截止、状态、重复、容量 |
| POST | `/api/activities/{id}/registrations/cancel` | 当前学生取消自己的报名；开始后不能取消 |
| GET | `/api/registrations/mine` | 当前账号报名及取消记录，不接收 userId 参数 |
| GET | `/api/manage/activities/{id}/registrations` | 本社团负责人读取当前有效报名名单，不公开给其他学生或社团 |
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

首次响应包含服务生成的 UUID `conversationId`，后续请求可传这个 ID。`mode=LOCAL` 表示原文摘录；`mode=OPENAI` 表示启用了模型接口（无依据时仍直接返回资料不足）。`references` 包含资料 ID、名称、片段号、摘录和检索得分，`retrieval` 表示本次实际采用的检索方式。

写请求需先 GET `/api/auth/session`，将 csrfToken 放入 csrfHeader 指定的请求头，同时保留 Session Cookie。登录、退出会轮换 CSRF 状态，下一次写请求重新取 token。前端 `request()` 已封装这一过程。

入社请求示例：`{"clubId":2,"reason":"希望学习摄影"}`；clubId 应从社团查询获取，不硬编码。审核示例：`{"approved":true,"feedback":"欢迎加入"}`。理由为 1～500 字，审核意见最多 300 字。

申请状态为 pending/approved/rejected/withdrawn。被拒或撤回后可新建申请，旧记录保留；已有待审核申请或已入社返回 409。审核与成员写入在同一事务中，重复审核返回 409。未登录返回 401，CSRF 错误或越权返回 403，记录不存在返回 404。

活动草稿请求：`{"title":"手机摄影交流","description":"校园构图练习","location":"学生中心 204","startTime":"2026-10-17T14:00:00","registrationDeadline":"2026-10-16T18:00:00","capacity":30}`。名称最多 100 字、介绍最多 2000 字、地点最多 200 字、容量 1—500 人。时间为校园本地时间（当前本机 Asia/Hong_Kong）；截止须在未来且不晚于开始。草稿保存后还需单独发布，不存在自动发布行为。报名无需先加入社团；负责人账号不能报名。取消保留记录，重新报名复用同一用户—活动记录，再次校验截止及容量。

站内通知由审核通过/拒绝、报名成功、取消成功自动产生，不提供客户端创建通知接口。业务与通知在同一事务中写入；重复/失败请求不产生通知，取消后重新报名是新事件。通知保存发生时的正文和时间，查看相关记录时显示当前业务状态。不补造接入前历史通知，不代表微信推送或实时消息。读取与已读操作只从 Session 获取账号身份。

错误采用 HTTP 状态码及 `detail` 文本。AI 服务不可用时返回 503，入社和活动发布/报名/通知业务不调用 AI。活动编辑、下架和签到待实现。AI 开发接口目前仍为公开测试资料，仅限本地环境；新增业务权限不代表 AI 数据权限已经完成。
