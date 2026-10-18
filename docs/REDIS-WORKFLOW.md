# Redis 接入与验收

Redis 用于公开社团资料缓存、账号级 AI 请求限流、短期 AI 会话缓存。数据库仍保存正式业务和完整聊天记录。报名唯一约束和名额事务未改为 Redis 实现。

## 开启

`.env.local`（不可提交）设置如下，然后重启两个后端。模板默认关闭，保留无 Redis 开发模式；本机联调配置已开启。

```dotenv
CAMPUS_REDIS_ENABLED=true
AI_REDIS_ENABLED=true
AI_REQUESTS_PER_MINUTE=5
REDIS_HOST=127.0.0.1
REDIS_PORT=16379
REDIS_PASSWORD=
```

已安装 `redis-server.exe` 时，运行 `powershell -ExecutionPolicy Bypass -File scripts/redis-local.ps1 start`；可将 start 换为 status/stop。脚本只管理登记的、使用本项目配置的 Redis，绑定 127.0.0.1:16379，不动原有 6379 服务。不负责下载安装；本次机器上的 Windows Redis 为旧版 3.0.504，只用于本机兼容性联调，不能据此声称 Redis 7 或生产环境已验收。应用启停脚本不负责 Redis 生命周期。

Docker 已运行时，也可运行 `docker compose -f infra/redis.compose.yml up -d`，只启动 Redis，不依赖 MySQL 的密码变量；不要同时占用 16379。

项目 Redis 使用 128MB 上限、noeviction，避免普通缓存淘汰限流键；满内存时缓存降级、新 AI 请求暂停。关闭持久化，重启丢失缓存和当前限流窗口，不影响正式业务记录，因此不是严格的费用账本。

## 请求与数据

| 功能 | Key | 过期 |
| --- | --- | --- |
| 社团资料 | `campus:business:club:v1:{clubId}` | 5 分钟 |
| AI 限流 | `campus:business:ai-rate:v1:{accountId}` | 首次请求起 60 秒 |
| AI 上下文 | `campus:ai:v2:{account+权限范围}:{conversationId}` | 刷新后 2 小时 |

社团详情先读缓存，未命中查库；不存在返回 404，不缓存。列表从数据库查当前 ID，再复用详情缓存。成员数量始终查库；推荐候选、入社资格和报名名额仍由数据库校验。负责人更新事务提交后写随机失效标记，回滚不失效；Lua 回填比较读取时的缓存值，避免旧读取覆盖尚未过期的失效标记。失效标记也为 5 分钟，不提供超长查询或跨实例故障下的强一致保证。本项目仍按单校园服务实例运行。

缓存异常时回源数据库，当前进程绕过社团缓存 5 分钟，避免失效失败后马上恢复读取旧缓存。Redis 恢复后这段时间仍可能查库，是预期行为。已打开的页面不推送更新，需要刷新。

问答、助手、推荐、策划、知识上传共用每账号一个请求窗口。身份及相应业务权限检查在先，限流在转发 AI 之前。Lua 原子完成计数、首次过期和阈值判断；超额返回 429、中文原因及 Retry-After 秒数。拒绝不增加计数、不延长窗口；获准转发后，即使 AI 参数校验或模型失败，也已占用一次次数。窗口边界可能短时跨窗口请求；不是滑动窗口或模型并发限制。账号来自后端登录态，不接受浏览器指定 userId。

开启校园 Redis 后，Redis 不可达/无法执行限流时，新 AI 操作返回 503，基础业务继续运行。历史读取不扣次数；管理员模型检测/测试/启用保留独立权限，不纳入学生业务额度。未新增充值、日费用账本、集群限额或自动云端回退。

短期对话通过数据库 findTop6ByConversationIdOrderByIdDesc 只取最近 6 轮，再按时间顺序进入原有上下文长度限制。仍检查会话归属和引用资料当前权限，缓存必须等于授权消息才使用；过期、损坏、不同的缓存会以授权内容修复，Redis 故障回退数据库。没有省去最近 6 轮查库，不宣称缓存命中不查库或推理加速。完整历史页面暂未分页；旧权限范围的缓存按 TTL 到期。

## 验证

```powershell
mvn -DskipTests=false test
# 独立项目 Redis 已启动时，额外验证真实 Lua、TTL、并发和会话缓存
mvn -DskipTests=false '-Dredis.test.port=16379' test
cd frontend
npm run build
cd ..
node scripts/check-redis.mjs flow
```

真实 Redis 测试使用 14/15 号库，只移除自身明确测试键，不执行 FLUSHDB/FLUSHALL；只指向本项目隔离 Redis。普通测试不强制依赖 Redis，两个真实 Redis 测试类会跳过。

HTTP flow 创建并保留独立虚构账号/社团。用空问题触发 AI 参数校验，不进行模型推理或付费调用；默认限额下前五次返回 400，第六次返回 429。需 AI 服务正在运行、额度配置为 5。

```powershell
powershell -ExecutionPolicy Bypass -File scripts/redis-local.ps1 stop
node scripts/check-redis.mjs offline
powershell -ExecutionPolicy Bypass -File scripts/redis-local.ps1 start
node scripts/check-redis.mjs restored
```

停启只适用于脚本登记的本地 Redis；Docker 方式需管理对应 compose。停止会丢失临时缓存和限流窗口，数据库记录保留。offline 检查公开查询、本人资料保存、历史读取正常，而新 AI 请求返回访问控制 503。restored 读取同组数据。真实浏览器点击、真机体验、Redis 7 容器和容量压测需要另行验收。

## 本轮结果（2026-10-18）

- 两个后端完整测试及打包通过，共 123 个测试（校园 71、AI 52），包含显式启用的真实 Redis 测试；前端类型检查及构建通过，保留原有大于 500KB 的 bundle 提示。
- 真实 Redis 下 30 个并发调用恰好允许 5 个、拒绝 25 个；验证账号隔离、TTL、过期后新窗口、缓存命中/失效及迟到回填保护、会话失效内容修复。
- 真实 HTTP flow/offline/restored 通过；关闭独立 Redis 时校园查询、资料保存与历史读取正常，新 AI 请求 503；恢复 Redis 和重启两个后端后同组业务数据仍可读取。
- 原聊天/Embedding 模型、启用时间和操作者经 restored 检查保持一致。未调用付费模型，没有修改旧业务验收记录；新增一个保留的虚构学生和社团。
- Windows Redis 3.0.504 兼容性验证不等于 Redis 7/生产验收。AI 短期缓存仍保留授权数据库核对，不宣称免查库或性能倍率。
