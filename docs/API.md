# 当前接口

前端和联调统一使用校园服务的 `/api`。AI 服务的 `/internal` 接口只用于内部开发通信，正式部署需增加网络与服务认证。

| 方法 | 校园服务路径 | 当前行为 |
|---|---|---|
| GET | `/api/system` | 已实现/示例/待实现模块状态 |
| GET | `/api/clubs` | 示例社团列表 |
| GET | `/api/clubs/{id}` | 社团详情，不存在返回 404 |
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

错误采用 HTTP 状态码及 ProblemDetail 的 `detail` 文本。AI 服务不可用时返回 503，校园只读查询不受影响。没有入社、报名、发布接口，也没有假装成功的写操作占位。
