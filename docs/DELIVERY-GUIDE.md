# 本机运行与毕业设计演示

## 启动

需要 Java 17、Maven、Node.js；可选 Ollama 与 Redis。默认使用两个独立 H2 文件库，MySQL profile 只提供切换配置，尚未完成正式库迁移验收。

```powershell
cd C:\workspace\campus-club-platform
powershell -ExecutionPolicy Bypass -File scripts/init-local.ps1
mvn -DskipTests=false package
cd frontend
npm ci
npm run build
cd ..
powershell -ExecutionPolicy Bypass -File scripts/start-dev.ps1
```

`init-local.ps1` 仅在密钥缺失/为空时生成随机的服务间 `AI_INTERNAL_TOKEN` 和独立的 `AI_CONFIG_MASTER_KEY`，保存到忽略的 `.env.local`，不打印密钥，不覆盖已有模型配置。两个后端共用服务间密钥；AI_CONFIG_MASTER_KEY 仅由 AI 服务用于加密平台管理员保存的模型密钥，应单独安全备份，丢失/更换后无法解密原密钥。IDEA 独立启动时须配置对应 Run Configuration，不会自动读取根目录文件。不能放进 VITE 配置、截图或 Git。

若本项目已运行，先执行 `scripts/stop-dev.ps1`，再打包；不要在 Windows 下直接覆盖正在使用的 JAR。启动后等日志显示两个服务 `Started`，再打开 [社遇](http://127.0.0.1:5178/)。端口为校园 8090、AI 8091、前端 5178；只监听本机，不提供公网服务。`.run/` 保存日志及测试记录，不上传 Git。

默认 LOCAL 无需模型。使用本机现有 Ollama 时，由用户打开 Ollama，保留共享密钥，并设置：

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

上述模型必须已经安装；脚本不会下载/替换模型，也不会管理用户 Ollama 进程。原生地址不带 `/v1`，无需为学生开付费账户。兼容模型服务的 API Key 仅由后端管理员配置，现阶段没有学生充值或计费页面。

## 账号与演示路线

demo 密码均为 `CampusDemo123!`，只用于本机虚构账号：`student`/`student2`、`photo_manager`/`code_manager`、`platform_admin`。真实学校部署不能启用 demo 配置或沿用这些密码。

建议用新注册的虚构学生进行展示，避免改旧的自动验收记录。负责人和平台管理员使用对应 demo 账号，不从页面切换角色来赋权。

1. 学生：社团列表/招新筛选 → 账号收藏 → 提交入社申请 → 查看“我的”记录与站内消息。
2. 负责人：社团资料与招新开关 → 申请审核 → 成员列表；关闭招新仍保留已有申请和成员。
3. 活动：新建或 AI 策划参考 → 修改 → 人工确认保存草稿 → 单独发布 → 学生报名 → 负责人名单/签到 → 查看签到时间。现场码只在开始前 30 分钟起能开启，不能为演示绕过时间规则。
4. 经费：设置活动预算 → 录入支出 → 显示余额/超支 → 误录作废保留原因。不是付款、报销审批或社团银行余额。
5. 平台：上传/调整首页海报并确认上下架；在 `/platform/models` 分别配置聊天与 Embedding，通过检测、保存、测试、确认启用提供全校统一 AI。活动取消、开始或招新结束后，相应海报按查询规则过滤。模型设置的安全边界与环境变量优先级见 [平台模型设置](PLATFORM-MODELS.md)。
6. AI：自由兴趣推荐展示语义与文字混合排序；上传社团内部 TXT/MD，分别用成员与非成员验证权限；在助手查看真实查询轨迹与本人历史。模型可能失败或回答有误，应展示来源核对和错误边界。

## 验证与证据

```powershell
mvn -DskipTests=false test
cd frontend
npm run build
npm run test:prototype
npm run test:business
npm run test:finance
npm run test:check-in
npm run test:club-settings
npm run test:ai-workflows
npm run test:model-settings
cd ..
node scripts/check-completion.mjs
```

最后一条只接受本机 OLLAMA 配置并实际推理。LOCAL 配置用 `node scripts/smoke.mjs`；不要为通过测试而将付费远程服务误当成本地模型。

完成 flow 后，执行 `restart-ai.ps1 -BackupData` 与 `restart-campus.ps1 -BackupData`，等待启动，再运行 `node scripts/check-completion.mjs restored`。只停止经核对的项目 AI 进程时，运行 `node scripts/check-completion.mjs offline` 验证基础业务；之后重新启动 AI。不要停止用户 Ollama 或其他项目服务。

`-BackupData` 在进程关闭后复制默认 H2 文件，不代表 MySQL 备份方案。不要在数据库写入时直接复制文件备份。旧无归属会话保留但无法读取；新的 flow 会生成有账号归属的验收记录，不能靠给旧记录随便绑定账号来通过 restored。

## 论文表述与边界

可围绕业务分析、独立服务与数据归属、事务/权限、混合兴趣匹配、带来源 RAG、受限只读 ReAct、活动人工确认和测试设计撰写。区分“实现了功能”和“测得效果”，不能将几条本机样例写成高并发/高准确率或生产安全结论。上游代码来源仍见 `THIRD-PARTY-NOTICES.md`，不得删除许可证或将复用描述为从零原创。

本机可运行不等于正式学校上线。仍未包含学校 SSO、密码找回、真实支付/发票/校级审批、任意角色管理、外部 MCP、DAG 引擎、多智能体、自动场地调度，以及正式 MySQL 迁移、HTTPS、限流和容量压测。界面使用响应式布局；SSR 与 HTTP 测试不等于真机触摸、浏览器逐页点击或无障碍全项验收。
