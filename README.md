# 校园社团活动管理与招新平台

这是一个可启动、可联通的精简毕业设计工程。校园业务后端已补齐本轮约定的范围，后续继续接入页面和 AI 能力。

Git 历史于 2026-10-08 根据先前开发记录分阶段重建，不是当时实时保存的提交，未回填提交日期。11 个功能阶段均通过编译和对应自动化测试；重建来源、验证结果及 AI 辅助/第三方代码边界见 [Git 历史整理说明](docs/GIT-HISTORY-RECOVERY.md)。

已实现：两个独立 Spring Boot 服务；Vue3 前端；学生注册、个人资料与兴趣接口；数据库社团查询及负责人创建/维护、招新开关与条件维护；Session 登录与所属社团权限；入社申请、审核、撤回和成员关系；活动草稿、编辑、确认发布、取消及报名者通知；报名/取消、名额与截止校验、负责人报名与签到名单；签到码开启/关闭与学生签到；活动预算、支出防重、作废留痕、社团汇总；账号收藏；站内结果通知、账号隔离、分页与已读状态；公开 UTF-8 文本上传、分块、检索、出处展示和会话记录；可配置的真实模型与 Embedding 接口；可选 Redis 短期上下文。

待实现：新增校园后端接口的页面接入、找回密码、正式学校身份认证、完整 AI 资料权限、AI 活动策划与人工确认、ReAct、MCP、DAG。兴趣推荐已用 Spring AI 接通本机 Ollama，并加入 BM25 与 RRF 融合排序；正式推荐效果评估仍待完成。当前页面明确区分数据库功能与演示功能。

注册与个人资料链已接入前端：`/login` 注册学生账号后登录，在 `/me` 编辑姓名、专业、兴趣描述、辅助标签和空闲时间，保存到当前账号数据库。`/assistant` 综合社团名称、类别、简介、标签，使用 Embedding + BM25，再以 RRF 融合排名；验收脚本社团退出推荐但保留业务记录。需配置模型，未配置时明确提示不可用，不显示模拟推荐。资料保存不调用 AI。见 [兴趣推荐说明](docs/INTEREST-RECOMMENDATION.md)。社团维护、活动编辑/取消和账号收藏已可通过 API 使用，页面仍待接入；签到和经费台账页面已接通；当前收藏页面仍是浏览器本地数据。范围、请求示例与源码路线见 [校园后端业务说明](docs/CAMPUS-BACKEND-WORKFLOW.md)，账号链使用与验收见 [账号与个人资料](docs/ACCOUNT-PROFILE-WORKFLOW.md)。

当前全部可访问前端页面已统一到用户认可的白底绿色社群风格，包括主入口、登录、详情、消息、负责人各栏目、使用说明及 `/system` 资料问答与联调页。表单与弹窗共用控件风格，保留原有业务记录和模式边界。此项是全页面视觉统一，不代表新增后端接口已全部接入页面，也没有新增 AI 能力。见 [全页面界面验证记录](docs/COMMUNITY-UI-VERIFICATION.md)。

首页内容管理已接通：新增 `PLATFORM_ADMIN` 角色，管理 PNG/JPG 海报上传、站内关联内容、顺序、展示时间及确认上下架；发现页显示数据库轮播，支持自动/手动切换和暂停。管理员不继承社团负责人权限。demo 账号 `platform_admin`，密码 `CampusDemo123!`，登录后进入 `/platform/banners`；手机也可从“我的”进入。仅 demo 配置创建三个虚构示例海报，纯前端原型不调用管理接口，旧原型归档不变。见 [首页内容管理说明与验收](docs/PLATFORM-BANNERS.md)。

经费台账页面已接通：负责人进入 `/manage/finance`，选择所属社团，查看活动预算、有效支出和剩余预算，设置预算、录入支出、确认作废并保留原记录。未设预算不是 0 元余额，汇总不是账户现金余额；不包含支付、报销或学校级审批。纯原型仍使用原有本地预算演示。用法、源码与验收见 [经费台账说明](docs/FINANCE-WORKFLOW.md)。

## 当前第一条真实业务

学生登录 → 查看社团 → 提交入社申请 → 负责人登录审核 → 同一事务保存审核状态与成员关系 → 学生重新登录查看结果。

正常开发服务地址为 `http://127.0.0.1:5178/login`。demo 模式账号：`student`、`student2`、`photo_manager`、`code_manager`；演示密码均为 `CampusDemo123!`。仅用于本机虚构数据，不是正式校园账号。负责人权限来自数据库，不是切换按钮赋予。

这条业务不依赖 AI、Redis 或外部模型。默认沿用 H2 文件库；不是已经切换 MySQL，也不是 JWT 登录。使用与源码学习路线见 [入社业务说明](docs/RECRUITMENT-WORKFLOW.md)。

## 先看产品原型（支持手机浏览器）

第二条数据库业务也已接通：负责人保存活动草稿 → 确认发布 → 学生报名/取消 → 负责人查看名单。打开 `http://127.0.0.1:5178/activities`，负责人入口 `/manage/activities`；账号沿用上述 demo 账号。面向全校学生，地点直接填文字，不包含场地预约或经费审批。签到页面已接入：负责人开启/关闭现场码，学生输入签到码，名单与“我的报名”显示签到时间。见 [签到链说明](docs/CHECK-IN-WORKFLOW.md)。界面延续用户确认的白底社群列表风格。使用与代码链见 [活动业务说明](docs/ACTIVITY-WORKFLOW.md)，实际验收见 [活动验证](docs/ACTIVITY-VERIFICATION.md)。

第三条通知链：审核结果、报名与取消成功 → 同一事务保存站内通知 → 消息中心查看相关记录、标为已读。打开 `/messages` 或顶部铃铛；没有实时推送，也不补造此前的历史通知。见 [通知业务说明](docs/NOTIFICATION-WORKFLOW.md) 和 [通知验证](docs/NOTIFICATION-VERIFICATION.md)。

纯原型模式展示“社遇”学生端及负责人工作台，可以模拟申请、审核、成员、活动草稿、发布、报名、收藏和消息；此模式的记录仍只保存在当前浏览器，助手保留明确标注的标签筛选。正常开发模式中，注册/资料、入社、活动发布/报名/签到、经费与消息已接后端，收藏仍仅存在浏览器；助手语义推荐按需调用已配置的 Embedding 模型。原有资料联调页保留在 `/system`。

只看原型，不需要 Java、Redis 或大模型：

```powershell
powershell -ExecutionPolicy Bypass -File C:\workspace\campus-club-platform\scripts\start-prototype.ps1
```

先在 `frontend` 安装依赖。电脑打开 `http://127.0.0.1:5178/`；手机与电脑接入同一可互通局域网，打开终端显示的 `Network` 地址。局域网原型模式不转发后端 API；如需资料问答联调，停止原型后按下文启动标准开发服务。勿把开发端口发布到公网。完整说明见 [原型说明](docs/PROTOTYPE.md)。

前端检查：`npm run build`、`npm run test:prototype`、`npm run test:business`。自动化脚本覆盖状态逻辑、请求封装与服务端渲染；另已完成第一条入社业务的实际浏览器点击验收，见 [验证记录](docs/RECRUITMENT-VERIFICATION.md)。

## 先运行起来

需要 Java 17、Maven、Node.js。后端沿用已有项目的 Spring Boot 3.4.2；前端采用 Vue 3.5.13 和 Vite 6.3.5。AI 服务使用 Spring AI 1.0.9 接入原生 Ollama 或 OpenAI-compatible 聊天与 Embedding；仅引入模型库、不启用模型自动下载。

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

已完成打包后，仅重启校园服务可执行 `powershell -ExecutionPolicy Bypass -File .\scripts\restart-campus.ps1 -BackupData`，AI 和前端进程不动。`-BackupData` 在校园进程关闭后复制默认 H2 文件到 `.run/campus-backup-时间/`，不代替 MySQL 的备份方案。打包前需停止正在占用 JAR 的校园进程，完整停服务方式仍为上面的 `stop-dev.ps1`。

校园后端新增接口联调：`node scripts/check-campus-backend.mjs`；重启后检查同一批记录：`node scripts/check-campus-backend.mjs restored`。首次运行会创建明确标注的虚构账号、社团和活动并保留记录，不改旧验收记录。

## IDEA 中怎么打开

使用 IDEA 的 Open 打开根目录，导入根 `pom.xml`，项目 SDK 选择 Java 17。两个模块分别启动：

- `campus-service` → `com.campus.business.CampusApplication`，端口 8090。
- `ai-service` → `com.campus.ai.AiApplication`，端口 8091。

前端终端在 `frontend` 目录执行 `npm run dev`。脚本启动与 IDEA 启动二选一，避免端口冲突。IDEA 中环境变量需要配置到对应 Run Configuration；根 `.env.local` 仅由启动脚本读取。

## 项目结构与请求路径

```text
campus-club-platform/
├─ campus-service/  注册/资料、社团/招新、成员、活动/报名/签到、经费/收藏/通知和 AI 转发
├─ ai-service/      资料、分块、检索、模型客户端、会话记录
├─ frontend/        Vue3 页面与请求封装
├─ docs/            架构、接口、开发顺序与验证记录
├─ infra/           可选 MySQL、Redis 配置
└─ scripts/         Windows 启停与联通检查

浏览器 → /api → campus-service:8090 → /internal → ai-service:8091
                  ↓ 校园数据库                    ↓ AI 数据库
```

两个后端分别拥有应用入口、进程、数据库与 HTTP 接口。当前没有注册中心、独立网关或跨服务公共 DTO 库。校园服务提供 AI 转发入口，前端不直接访问 AI 服务。

## 问答模式

默认 `AI_MODE=local`：无需数据库安装和模型密钥。H2 文件库分别位于各后端工作目录的 `data/`；上传文本后进行关键词检索，返回原文摘录与出处。**LOCAL 问答不调用 LLM、不做语义检索；兴趣推荐可独立启用 Embedding。** 不配置 Embedding 时，推荐明确提示不可用。

真实模型模式：复制 `.env.example` 为 `.env.local`，设置 `AI_MODE=openai`，填写 `CHAT_BASE_URL`、`CHAT_MODEL`、`CHAT_API_KEY`、`EMBEDDING_BASE_URL`、`EMBEDDING_MODEL`、`EMBEDDING_API_KEY`。文件使用 `KEY=VALUE`，值不加引号，也不执行变量替换。Base URL 填兼容接口的根路径，例如服务提供的 `/v1` 根地址；不要填完整 `/chat/completions` 或 `/embeddings` 路径。更改后重新启动本项目。

真实模式下重新上传资料，才会生成与当前模型对应的向量。更换 Embedding 模型或维度应重新建立索引；本地模式旧资料仍可被关键词检索。启动服务不会自动调用付费模型。模型故障返回明确错误，不偷偷用本地摘录冒充模型回答。

## 本机 Ollama 兴趣推荐

先打开 Ollama，确认已经安装 `qwen3-embedding:0.6b`。根目录忽略的 `.env.local` 设置：

```dotenv
AI_MODE=local
AI_REDIS_ENABLED=false
EMBEDDING_PROVIDER=ollama
EMBEDDING_BASE_URL=http://127.0.0.1:11434
EMBEDDING_MODEL=qwen3-embedding:0.6b
EMBEDDING_API_KEY=
```

原生 Ollama 地址不带 `/v1`，调用 `/api/embed`，本机默认不需要 API Key。不要把模型地址或密钥交给学生填写。上述 `AI_MODE=local` 配置只启用真实语义推荐，资料问答仍返回原文摘录。

脚本启动后，登录 → “我的”编辑兴趣描述 → “助手”按兴趣推荐。已运行时修改配置后执行 `powershell -ExecutionPolicy Bypass -File .\scripts\restart-ai.ps1 -BackupData`，只重启 AI 服务，不动校园服务、前端或 Ollama。首次加载可能较慢；模型读取超时为 60 秒，失败会明确提示，不显示伪造推荐。

实际模型验收：`node scripts/check-ollama-recommendation.mjs`；重启 AI 后 `node scripts/check-ollama-recommendation.mjs restored`。两者使用独立虚构账号，不改现有学生资料。未配置模型的旧验收脚本 `check-interest-recommendation.mjs` 不用于此配置。详情与测得的分数见 [兴趣推荐说明](docs/INTEREST-RECOMMENDATION.md)。

## 本机 Ollama 资料问答

在上面的原生 Embedding 配置基础上，将 `AI_MODE` 改为 `ollama`，增加 `CHAT_BASE_URL=http://127.0.0.1:11434`、`CHAT_MODEL=qwen2.5:3b`、`CHAT_API_KEY=`。必须先安装模型并由用户打开 Ollama，项目不会代为下载模型。

重新启动 AI 后，在 `/system/knowledge` 重新上传公开测试资料建立当前模型索引，在 `/system/chat` 提问；也可以从 `/assistant` 的“打开资料问答”进入。流程为上传→分块→向量存储→关键词与向量检索→Spring AI 调用聊天模型→保存回答与原文出处。旧资料与历史引用保留，不自动批量重建索引。模型回答不是审核结论，不能替代实时业务查询。

使用 `node scripts/check-ollama-rag.mjs` 验收，`restored` 检查重启后的资料、回答和引用，`offline` 检查只停止项目 AI 后校园业务仍可查询。原 `scripts/smoke.mjs` 继续限定 LOCAL 模式，防止误调用外部收费模型。具体实现、使用方法和验收边界见 [RAG 问答说明](docs/RAG-WORKFLOW.md)。

## 当前边界

- 服务默认只监听 `127.0.0.1`，仅用于本地开发；已实现入社业务登录与权限，但 AI 联调接口仍使用公开测试资料，尚无完整用户/社团资料隔离及限流。不得因新增登录就公开部署。
- 只支持 `.md` / `.txt`，UTF-8 编码，单文件 128KB、最多 128 分块；PDF、Word、OCR、大文件异步上传未实现。
- 当前向量存在 AI 数据库，以精确余弦扫描支持小规模数据；这不是 Elasticsearch 或大规模 ANN 检索实现。后续有需要再迁移检索组件。
- 默认不启用 Redis；设置 `AI_REDIS_ENABLED=true` 后缓存最近 6 轮对话两小时，完整问答和引用始终在 AI 数据库。
- 默认 H2 是方便起步的开发数据库；后续切换 MySQL，并用版本化迁移管理正式表结构。`ddl-auto=update` 仅用于开发。

可选 MySQL/Redis 操作见 `docs/ARCHITECTURE.md`。开发入口见 `docs/IMPLEMENTATION-ROADMAP.md`。代码来源见 `THIRD-PARTY-NOTICES.md`。
