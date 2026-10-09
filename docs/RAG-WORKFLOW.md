# 公开资料 RAG 问答

## 本次交付范围

复用现有 PaiSmart 精简链：公开资料上传、分块、向量保存、检索、回答与出处、持久会话。聊天接入迁移到 Spring AI 1.0.9，支持原生 Ollama 与 OpenAI-compatible；没有更改原 PaiSmart 工程、数据库或密钥，也没有引入 ReAct、MCP、DAG、消息队列或业务自动执行。

此功能是本机公开虚构资料演示，不是学校正式知识库。AI 会话尚未绑定用户，文档尚未按社团授权，不得上传私人资料或公开部署。

## 一个完整请求

1. 在“助手 → 公开测试资料”（`/system/knowledge`）上传 UTF-8 `.md` 或 `.txt`。
2. AI 服务按段落和句子分块，目标长度 600 字符，重叠 80 字符；最多 128 块，文件最多 128KB。
3. OLLAMA 模式调用 `/api/embed`，批次最多 32 条，校验向量数量、索引、维度、有限值与非零范数，保存文档、片段与当前模型版本。模型失败不会留下半篇资料。
4. 在“助手 → 打开资料问答”（`/system/chat`）提问。前端带 CSRF 和 Session 调校园服务；校园服务转发 AI 请求，不把正式业务表交给模型维护。
5. 对当前问题编码，扫描小规模公开片段：只有相同模型版本的向量参与余弦计算，同时计算文字命中；取最多 4 个片段。旧资料仍可参与关键词检索。
6. 无片段时不调用聊天模型，直接说明缺少依据。有片段时，Spring AI ChatModel 将系统约束、最近 6 轮历史和本次原文发送到 `/api/chat`。
7. 模型整理回答并使用 `[1]` 等编号。回答、问题、实际检索片段与模式存入 AI 数据库；可展开出处核对。失败时不保存该轮、不冒充本地摘录。

普通校园查询、个人资料保存、申请、审核和报名不走该链。该助手只读，不会代为报名、审批或发布。

## 模式与配置

| 配置 | 问答行为 | 新上传资料 |
|---|---|---|
| `AI_MODE=local` | 关键词检索后显示原文，不调用聊天模型 | 关键词资料 |
| `AI_MODE=ollama` | 原生 Ollama ChatModel 整理回答 | 配置 Embedding 后建立向量索引 |
| `AI_MODE=openai` | OpenAI-compatible ChatModel 整理回答 | 配置 Embedding 后建立向量索引 |

Embedding Provider 可以独立选择。`auto` 跟随模式：LOCAL→NONE、OLLAMA→OLLAMA、OPENAI→OPENAI。所以 LOCAL 问答仍可配 OLLAMA 兴趣推荐，两者不混淆。

本机已安装模型的配置示例，写入根目录忽略的 `.env.local`：

```dotenv
AI_MODE=ollama
AI_REDIS_ENABLED=false
CHAT_BASE_URL=http://127.0.0.1:11434
CHAT_MODEL=qwen2.5:3b
CHAT_API_KEY=
EMBEDDING_PROVIDER=ollama
EMBEDDING_BASE_URL=http://127.0.0.1:11434
EMBEDDING_MODEL=qwen3-embedding:0.6b
EMBEDDING_API_KEY=
```

由用户打开 Ollama。项目不启动、停止或下载 Ollama 模型。原生地址不加 `/v1`；兼容模式则使用供应商提供的兼容根路径，例如 `/v1`。模型选择由服务端配置，学生不填 Key、不充值模型账户。

已运行项目中，修改配置后只重启 AI：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\restart-ai.ps1 -BackupData
```

代码更新时必须先停止经过进程校验的项目 AI，再打包 JAR，避免 Windows 文件占用。备份的是关闭后的开发 H2 文件，不是正式数据库迁移。

## 索引与会话保留

- 启动不会批量调用模型或重建索引。旧资料与历史引用保留。
- 同内容、同 Embedding 版本重复上传返回同一资料 ID；模型或版本改变后重新上传，会建立新版本资料，不删除旧资料。
- 历史引用保存的是当时摘录，不是每次读取都重新检索。相同文本的不同版本可能同时出现在来源列表中。
- 向量保存在 AI 数据库，采用精确扫描，不是独立向量数据库或 ANN。当前本机使用 H2；MySQL 和 Redis 尚未实测。
- 默认会话读取数据库；可选 Redis 缓存最近 6 轮两小时，Redis 不可用仍读数据库。模型上下文并非无限记忆。
- 追问会带近期问答，但检索仍基于当前问题。最好重复主题，例如“光影摄影社手机拍摄学习什么”，纯代词追问不保证召回前一主题。

## 检索与可信度边界

资料问答沿用简单的关键词命中 + 余弦融合（0.25/0.75，向量低于 0.35 且关键词低于 0.2 时过滤）。**它不是社团推荐的 BM25 + RRF**，两条链的算法与得分不可混为一谈。阈值尚未做正式效果校准，可能召回无关资料或漏召回。

提示词要求只根据本次资料回答、缺少事实时说明、忽略资料中的指令、不要把历史模型回答当事实。没有依据时由服务端直接拒答；但“召回了片段却不足以回答”仍依赖模型遵守提示，不能保证零幻觉或抵御所有提示注入。

页面明确标注 AI 生成与未找到依据，保留原文展开入口。检索来源不代表生成结论已人工审核，引用编号目前未在服务端强制校验。名额、报名状态、实际活动时间、审批和经费信息必须以校园业务系统为准。

Ollama 同步生成的参数为温度 0.2、上下文 8192、最多 600 个生成 token；模型连接超时 5 秒、单次读取 60 秒，无应用重试、无注册工具。首次加载较慢，超时需要用户重试；没有流式输出、队列、并发配额或完整限流，不宣称多用户性能。

## 验收命令

```powershell
mvn '-DskipTests=false' test
cd frontend
npm run build
npm run test:prototype
npm run test:business
cd ..
node scripts/check-ollama-rag.mjs
node scripts/check-ollama-rag.mjs restored
node scripts/check-ollama-recommendation.mjs restored
node scripts/check-campus-backend.mjs restored
```

`flow` 上传三个保留的公开虚构样本，并验证重复上传、出处、模型回答、明确主题的追问、资料没有说明的报名费。它只接受原生 OLLAMA 模式，防止调用收费接口。`restored` 比对已保存的全文、版本、回答与引用，不触发生成。

只停止经过校验的项目 AI 时运行 `node scripts/check-ollama-rag.mjs offline` 和 `node scripts/check-ollama-recommendation.mjs offline`，验证问答不可用而校园查询/个人资料仍可用。之后恢复项目 AI，不停止用户的 Ollama。

原 `scripts/smoke.mjs` 保持 LOCAL-only，用于不依赖模型的摘录模式。不能在 OLLAMA 模式使用它或把它改成可任意调用收费模型。

验收中的模型与资料都是小样本，协议测试使用合成向量，不是正式检索质量评估。前端构建与 SSR 不等于实际浏览器点击、手机体验或可访问性验收。

## 2026-10-09 实际验证

- 后端校园服务 57 项、AI 服务 35 项，共 92 项自动化测试通过；其中新增原生 Ollama RAG 集成测试 5 项。OpenAI-compatible 原有问答测试保留并通过。
- 前端构建、业务回归通过；纯原型 219 条断言、28 条路由 SSR 通过。本轮没有进行实际浏览器点击或真机验证。
- 本机 `qwen2.5:3b` + `qwen3-embedding:0.6b`：三份公开虚构资料建立索引，重复上传返回同一资料 ID。旧资料保留，当前资料总数 6。
- 问“光影摄影社零基础同学可以参加吗？需要自己买相机吗？”：回答“欢迎零基础……手机即可参与练习。[1]”，第一出处为对应摄影指南。
- 问手机拍摄入门内容：回答包含构图、自然光和校园记录，返回原文来源；该次模型未输出正文编号，说明提示词不能保证每次编号格式，原文仍可展开核对。
- 问 2028 年报名费：回答“资料未说明”，没有编造具体金额。三次问答及历史读取约 32.7 秒，仅是当次本机结果，不是性能基准。
- 只停止项目 AI：问答与推荐返回 503；校园查询、个人资料保存/读取正常。未停止校园、前端或 Ollama。
- AI 重启后：逐项比对资料全文、模型版本、问答正文和引用，通过；原推荐多兴趣样本仍返回程序设计社与摄影社，原校园后端保留记录验收通过。

运行证据保存在忽略的 `.run/ollama-rag-fixture.json`；不提交运行数据库或私有环境配置。以上不等于完整权限、零幻觉或生产性能验收。

接入依据：Spring AI 1.0 [Ollama Chat 文档](https://docs.spring.io/spring-ai/reference/1.0/api/chat/ollama-chat.html)，与工程依赖对应的 [v1.0.9 OllamaChatModel 源码](https://github.com/spring-projects/spring-ai/blob/v1.0.9/models/spring-ai-ollama/src/main/java/org/springframework/ai/ollama/OllamaChatModel.java)。第三方改造范围见 `THIRD-PARTY-NOTICES.md`。
