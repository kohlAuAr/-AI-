# 校园社团活动管理与招新平台

这是一个可启动、可联通的精简开发骨架。接下来在这份项目内逐项实现校园业务。

已实现：两个独立 Spring Boot 服务；Vue3 前端；数据库社团查询；Session 登录与所属社团权限；入社申请、审核、撤回和成员关系；活动草稿、确认发布、报名/取消、名额与截止校验、负责人报名名单；公开 UTF-8 文本上传、分块、检索、出处展示和会话记录；可配置的真实模型与 Embedding 接口；可选 Redis 短期上下文。

待实现：注册、找回密码、正式学校身份认证、社团与招新条件维护、完整 AI 资料权限、活动编辑/下架/签到、通知、兴趣推荐、AI 活动策划与人工确认、ReAct、MCP、DAG。当前页面明确区分数据库功能与演示功能。

## 当前第一条真实业务

学生登录 → 查看社团 → 提交入社申请 → 负责人登录审核 → 同一事务保存审核状态与成员关系 → 学生重新登录查看结果。

正常开发服务地址为 `http://127.0.0.1:5178/login`。demo 模式账号：`student`、`student2`、`photo_manager`、`code_manager`；演示密码均为 `CampusDemo123!`。仅用于本机虚构数据，不是正式校园账号。负责人权限来自数据库，不是切换按钮赋予。

这条业务不依赖 AI、Redis 或外部模型。默认沿用 H2 文件库；不是已经切换 MySQL，也不是 JWT 登录。使用与源码学习路线见 [入社业务说明](docs/RECRUITMENT-WORKFLOW.md)。

## 先看产品原型（支持手机浏览器）

第二条数据库业务也已接通：负责人保存活动草稿 → 确认发布 → 学生报名/取消 → 负责人查看名单。打开 `http://127.0.0.1:5178/activities`，负责人入口 `/manage/activities`；账号沿用上述 demo 账号。面向全校学生，地点直接填文字，不包含场地预约、签到或经费审批。界面延续用户确认的白底社群列表风格。使用与代码链见 [活动业务说明](docs/ACTIVITY-WORKFLOW.md)，实际验收见 [活动验证](docs/ACTIVITY-VERIFICATION.md)。

纯原型模式展示“社遇”学生端及负责人工作台，可以模拟申请、审核、成员、活动草稿、发布、报名、收藏和消息；此模式的记录仍只保存在当前浏览器。正常开发模式中，入社及活动发布/报名已接后端，收藏与消息仍为演示。AI 助手未在本次扩展。原有资料联调页保留在 `/system`。

只看原型，不需要 Java、Redis 或大模型：

```powershell
powershell -ExecutionPolicy Bypass -File C:\workspace\campus-club-platform\scripts\start-prototype.ps1
```

先在 `frontend` 安装依赖。电脑打开 `http://127.0.0.1:5178/`；手机与电脑接入同一可互通局域网，打开终端显示的 `Network` 地址。局域网原型模式不转发后端 API；如需资料问答联调，停止原型后按下文启动标准开发服务。勿把开发端口发布到公网。完整说明见 [原型说明](docs/PROTOTYPE.md)。

前端检查：`npm run build`、`npm run test:prototype`、`npm run test:business`。自动化脚本覆盖状态逻辑、请求封装与服务端渲染；另已完成第一条入社业务的实际浏览器点击验收，见 [验证记录](docs/RECRUITMENT-VERIFICATION.md)。

## 先运行起来

需要 Java 17、Maven、Node.js。后端沿用已有项目的 Spring Boot 3.4.2；前端采用 Vue 3.5.13 和 Vite 6.3.5。当前没有 Spring AI 依赖，通过独立模型客户端接入兼容接口。

在 PowerShell 中执行。如果本项目已经运行，先执行 `scripts/stop-dev.ps1` 停止本项目，再重新打包：

```powershell
cd C:\workspace\campus-club-platform
mvn -DskipTests=false package
cd frontend
npm ci
cd ..
powershell -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1
```

等待约 15 秒，打开 http://127.0.0.1:5178 。可执行 `node scripts/smoke.mjs` 检查前端代理、两个服务、资料上传、问答与历史。停止本项目：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-dev.ps1
```

启动和停止脚本只管理本项目登记的三个进程。启动前检查 8090、8091、5178 端口；日志在 `.run/`。没有修改原 PaiSmart。

## IDEA 中怎么打开

使用 IDEA 的 Open 打开根目录，导入根 `pom.xml`，项目 SDK 选择 Java 17。两个模块分别启动：

- `campus-service` → `com.campus.business.CampusApplication`，端口 8090。
- `ai-service` → `com.campus.ai.AiApplication`，端口 8091。

前端终端在 `frontend` 目录执行 `npm run dev`。脚本启动与 IDEA 启动二选一，避免端口冲突。IDEA 中环境变量需要配置到对应 Run Configuration；根 `.env.local` 仅由启动脚本读取。

## 项目结构与请求路径

```text
campus-club-platform/
├─ campus-service/  登录、社团查询、入社申请/审核/成员、活动发布/报名和 AI 转发
├─ ai-service/      资料、分块、检索、模型客户端、会话记录
├─ frontend/        Vue3 页面与请求封装
├─ docs/            架构、接口、开发顺序与验证记录
├─ infra/           可选 MySQL、Redis 配置
└─ scripts/         Windows 启停与联通检查

浏览器 → /api → campus-service:8090 → /internal → ai-service:8091
                  ↓ 校园数据库                    ↓ AI 数据库
```

两个后端分别拥有应用入口、进程、数据库与 HTTP 接口。当前没有注册中心、独立网关或跨服务公共 DTO 库。校园服务提供 AI 转发入口，前端不直接访问 AI 服务。

## 两种问答模式

默认 `AI_MODE=local`：无需数据库安装和模型密钥。H2 文件库分别位于各后端工作目录的 `data/`；上传文本后进行关键词检索，返回原文摘录与出处。**本地模式没有 Embedding，也不生成 AI 回答。**

真实模型模式：复制 `.env.example` 为 `.env.local`，设置 `AI_MODE=openai`，填写 `CHAT_BASE_URL`、`CHAT_MODEL`、`CHAT_API_KEY`、`EMBEDDING_BASE_URL`、`EMBEDDING_MODEL`、`EMBEDDING_API_KEY`。文件使用 `KEY=VALUE`，值不加引号，也不执行变量替换。Base URL 填兼容接口的根路径，例如服务提供的 `/v1` 根地址；不要填完整 `/chat/completions` 或 `/embeddings` 路径。更改后重新启动本项目。

真实模式下重新上传资料，才会生成与当前模型对应的向量。更换 Embedding 模型或维度应重新建立索引；本地模式旧资料仍可被关键词检索。启动服务不会自动调用付费模型。模型故障返回明确错误，不偷偷用本地摘录冒充模型回答。

## 当前边界

- 服务默认只监听 `127.0.0.1`，仅用于本地开发；已实现入社业务登录与权限，但 AI 联调接口仍使用公开测试资料，尚无完整用户/社团资料隔离及限流。不得因新增登录就公开部署。
- 只支持 `.md` / `.txt`，UTF-8 编码，单文件 128KB、最多 128 分块；PDF、Word、OCR、大文件异步上传未实现。
- 当前向量存在 AI 数据库，以精确余弦扫描支持小规模数据；这不是 Elasticsearch 或大规模 ANN 检索实现。后续有需要再迁移检索组件。
- 默认不启用 Redis；设置 `AI_REDIS_ENABLED=true` 后缓存最近 6 轮对话两小时，完整问答和引用始终在 AI 数据库。
- 默认 H2 是方便起步的开发数据库；后续切换 MySQL，并用版本化迁移管理正式表结构。`ddl-auto=update` 仅用于开发。

可选 MySQL/Redis 操作见 `docs/ARCHITECTURE.md`。开发入口见 `docs/IMPLEMENTATION-ROADMAP.md`。代码来源见 `THIRD-PARTY-NOTICES.md`。
