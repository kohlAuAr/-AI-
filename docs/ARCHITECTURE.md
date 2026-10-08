# 架构与数据归属

校园服务持有社团、活动等校园业务；AI 服务持有资料文档、分块、向量和会话。两个服务不共享表，也不把报名、成员等正式记录交给模型维护。学生填写兴趣画像后，画像仍应归校园服务；AI 服务仅接收授权的推荐输入。

## 已有实现

前端统一调用 `/api`，Vite 在开发环境转发到校园服务。校园服务的 `/api/ai/*` 再以 HTTP 请求 AI 服务的 `/internal/*`。AI 服务暂不回调校园业务；未来业务查询工具可以调用校园服务的只读接口。

资料处理沿用 PaiSmart 的主要处理链：文件文本 → 段落与句子分块 → 可选真实 Embedding → 保存文档与分块 → 关键词或向量融合检索 → 回答及引用 → 持久会话。重型上传、流式状态和商业额度已从迁移范围剔除。此处是代码来源说明，不是论文中的业务介绍。

当前小数据检索直接遍历 AI 数据库中的公开片段；向量使用相同模型标识下的余弦相似度，与关键词得分按 0.75/0.25 融合。这是资料检索的简单起点，**不是已经完成的学生社团推荐算法**。召回、阈值、关键词切词和权重需在后续用样本实验校准。

会话历史及引用在 AI 数据库持久保存。Redis 可缓存最近 6 轮模型上下文，缓存不可用时读取数据库。资料全文和模型密钥不放进前端配置。

## 可选 MySQL 与 Redis

现有电脑的 3306 和 6379 可能属于其他项目，本项目的 compose 使用 13306 和 16379。手动设置开发环境密码后运行：

```powershell
$env:MYSQL_ROOT_PASSWORD = '填写开发环境root密码'
$env:DB_PASSWORD = '填写campus账号密码'
docker compose -f infra/compose.yml up -d
```

在 `.env.local` 中设置：

```text
SPRING_PROFILES_ACTIVE=mysql
DB_HOST=127.0.0.1
DB_PORT=13306
DB_USER=campus
DB_PASSWORD=填写与compose一致的密码
AI_REDIS_ENABLED=true
REDIS_HOST=127.0.0.1
REDIS_PORT=16379
```

两个 MySQL 库是 `campus_business` 和 `campus_ai`。启用 mysql profile 后不自动装载模拟社团和资料。H2 数据不会自动迁移到 MySQL。初始化 SQL 仅在新的 compose MySQL 数据卷上运行；复用数据卷时需核对两个库是否已创建。

## 后续扩展点

1. 身份：Session 登录及入社业务所属社团校验已完成；后续补注册、学校身份验证、账号管理和其他模块权限。当前无需 JWT。
2. 知识权限：文档增加所属社团及可见范围，检索前根据调用身份过滤；不能只在展示阶段隐藏资料。
3. 工具：先增加 `get_club_detail`、`get_recruitment_status` 等只读业务接口，再建立工具注册表；MCP 适配器不替代服务间 HTTP。
4. 活动策划：固定需求检查与草稿生成，人工确认后由校园服务保存；后续需要时再引入显式 DAG 与受限 ReAct。
5. 规模：确认资料数量、查询耗时与部署条件后，再考虑 Elasticsearch、对象存储和异步任务。

除第 1 项中明确标注的已完成部分，以上仍为后续扩展，不应描述为已经交付。

## 入社业务的数据与事务

`campus_account` 保存 BCrypt 密码与角色；`club` 保存招新状态与条件；`club_application` 保存申请、审核人、审核时间、意见及状态；`club_membership` 保存用户—社团关系，角色为 MANAGER/MEMBER。

用户身份取自 Spring Security Session，负责人关系从 `club_membership` 读取。数据库中的申请不是原型 localStorage 的迁移或替身。成员计数来自实际成员关系，不沿用原型的宣传人数。

提交申请锁定社团记录，再检查招新状态、已有成员及 activeKey；activeKey 的唯一约束兜底防重。拒绝/撤回释放唯一槽，历史记录保留。审核和撤回锁定申请记录，只有 pending 可继续处理。批准时先写成员关系，再更新申请，两步处于同一事务；成员唯一键为 user_id+club_id。

当前为小规模开发实现，按社团串行化提交，列表暂未分页；不宣称高并发性能。后续按测量结果完善查询批量加载、分页与更细粒度并发控制。

默认 H2 的 `ddl-auto=update` 只用于开发。本次兼容升级已有示例社团的 slug/招新字段，不删除原有记录、不改变旧活动的社团 ID；正式 MySQL 上线前仍需版本化迁移和实例验证。demo profile 才会初始化公开演示账号，不应在生产启用。
