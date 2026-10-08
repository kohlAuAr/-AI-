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
| GET | `/api/activities` | 示例活动列表 |
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

错误采用 HTTP 状态码及 `detail` 文本。AI 服务不可用时返回 503，入社业务不调用 AI。活动报名、发布接口仍未实现，不提供假装成功的写操作占位。AI 开发接口目前仍为公开测试资料，仅限本地环境；新增入社权限不代表 AI 数据权限已经完成。
