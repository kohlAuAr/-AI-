# 校园社团活动管理与招新平台

这是一个可启动、可联通的精简开发骨架。接下来在这份项目内逐项实现校园业务。

已实现：两个独立 Spring Boot 服务；Vue3 前端；社团和活动的示例只读查询；公开 UTF-8 文本上传、分块、检索、出处展示和会话记录；可配置的真实模型与 Embedding 接口；可选 Redis 短期上下文。

待实现：登录与权限、招新维护、入社申请与审核、成员关系、活动发布与报名、通知、兴趣推荐、活动策划与人工确认、ReAct、MCP、DAG。当前页面明确标注了示例与待实现状态。

## 先看产品原型（支持手机浏览器）

默认首页已改为“社遇”校园社团产品原型，包含学生端及负责人工作台。可以演示申请、审核、成员、活动草稿、发布、报名、收藏和消息；这些记录只保存在当前浏览器，**上述正式后端功能仍待实现**。AI 仅保留辅助入口。原有真实接口联调页迁至 `/system`。

只看原型，不需要 Java、Redis 或大模型：

```powershell
powershell -ExecutionPolicy Bypass -File C:\workspace\campus-club-platform\scripts\start-prototype.ps1
```

先在 `frontend` 安装依赖。电脑打开 `http://127.0.0.1:5178/`；手机与电脑接入同一可互通局域网，打开终端显示的 `Network` 地址。局域网原型模式不转发后端 API；如需资料问答联调，停止原型后按下文启动标准开发服务。勿把开发端口发布到公网。完整说明见 [原型说明](docs/PROTOTYPE.md)。

前端检查：`npm run build`、`npm run test:prototype`。测试覆盖状态逻辑与页面服务端渲染，不等于真机或浏览器点击验收。

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
├─ campus-service/  校园正式业务的后端；目前是示例社团、活动查询和 AI 转发
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

- 服务默认只监听 `127.0.0.1`，仅用于本地开发；登录、资料权限与限流完成后再考虑公开部署。全部资料当前都是公开测试资料。
- 只支持 `.md` / `.txt`，UTF-8 编码，单文件 128KB、最多 128 分块；PDF、Word、OCR、大文件异步上传未实现。
- 当前向量存在 AI 数据库，以精确余弦扫描支持小规模数据；这不是 Elasticsearch 或大规模 ANN 检索实现。后续有需要再迁移检索组件。
- 默认不启用 Redis；设置 `AI_REDIS_ENABLED=true` 后缓存最近 6 轮对话两小时，完整问答和引用始终在 AI 数据库。
- 默认 H2 是方便起步的开发数据库；后续切换 MySQL，并用版本化迁移管理正式表结构。`ddl-auto=update` 仅用于开发。

可选 MySQL/Redis 操作见 `docs/ARCHITECTURE.md`。开发入口见 `docs/IMPLEMENTATION-ROADMAP.md`。代码来源见 `THIRD-PARTY-NOTICES.md`。
