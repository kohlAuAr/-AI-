# 自由文本兴趣与社团语义推荐

## 使用与边界

登录 → 我的 `/me` → 编辑资料 → 填写兴趣描述 → 保存 → 助手 `/assistant` → 按我的兴趣推荐。

例如：“喜欢用手机记录校园生活，想学拍照和剪视频，零基础，希望社团氛围轻松一点。”描述最多 1000 字，标签仅辅助展示，不参与当前语义分数。只选标签而没有描述时不生成推荐。

学生描述与社团名称、类别、简介、标签分别调用同一 Embedding 模型；用余弦相似度排序，最多展示 3 个分数大于 0 的招新社团。当前没有经验证的质量阈值；Top-3 是候选参考，不是适合率或录取概率。展示的“匹配参考”是数据库社团简介，不是大模型生成的个性化解释。

校园服务在模型调用前过滤招新关闭状态，返回前重新检查。文字形式的招新条件、空闲时间无法自动判断资格或课表冲突，页面提醒本人核对。未实现行为画像、LLM 解释、ReAct、MCP、DAG，也没有自动报名。

## 数据与服务

- `campus_account.interest_description` 保存原文；原账号新增列可为空，读出为空字符串。PUT `/api/profile` 可清空描述，旧客户端省略该字段时保留已保存描述。
- POST `/api/ai/recommendations` 要求 Session / CSRF，校园服务读取当前账号，不采信请求中的 userId、兴趣文本或候选社团。
- 校园服务仅向 POST `/internal/recommendations` 发送兴趣描述与公共社团文本，不发送姓名、账号、密码、专业等身份信息。
- AI 的 `interest_vector` 保存派生向量，不保存兴趣原文或账号编号。缓存主键为 SHA-256（编码流程版本 + Embedding 地址/模型版本 + 文本）；文本改变或模型地址/名称改变后，不使用旧向量。向量本身仍是敏感派生数据，不等于彻底匿名。
- 缓存保存在 AI 数据库，模型调用不持有跨网络数据库事务。当前小数据版本串行填充缓存、全量余弦扫描；未做高并发压测、缓存淘汰或大型 ANN 索引，不声称高并发能力。
- 个人资料保存、社团查询、招新申请均不调用模型。AI 停机、模型失败或未配置时，只有推荐返回明确失败；没有标签/模拟结果冒充语义推荐。

源码：校园 `identity/ProfileService`、`ai/RecommendationController` → `AiServiceClient` → AI `recommendation/RecommendationController`、`RecommendationService`、`InterestVectorRepository` → 既有 `client/ModelClient.embed()`。前端 `CommunityProfileEditor` → `business.saveProfile()`；`CommunityAssistantView` → `recommendations.loadRecommendations()`。切换账号或修改兴趣清理旧推荐，迟到响应不能覆盖新账号结果。

## 配置真实模型

默认 LOCAL 不产生向量。通过已有 `.env.local` 设置 `AI_MODE=openai`、`EMBEDDING_BASE_URL`（兼容接口根路径）、`EMBEDDING_MODEL`，需要鉴权时设置 `EMBEDDING_API_KEY`。只运行推荐不需要配置 CHAT_MODEL，也不调用聊天生成接口。模型必须支持当前原文编码方式和中文语义匹配；需要 query/document 专用前缀的模型，应先明确其编码要求再适配，不能随意混用不同向量空间。

启用后实际调用 `/embeddings`，不是模型名称存在就能证明可用。`/api/ai/status` 的 recommendation 状态只表示配置，不代表健康检查。模型权重如果原地替换却保留同一地址和名称，需更换版本化模型名称或在停机备份后清理对应缓存；当前不做权重自动探测。

不用把模型 Key 放到前端，不要求学生充值。学校自建模型或统一采购接口均由服务端配置。本次没有下载模型、配置密钥或调用收费服务，真实中文推荐质量仍待验收。

## 验证方法

- `mvn -DskipTests=false test` / `package`：校园推荐测试覆盖身份、CSRF、描述持久化、旧请求兼容、长度限制、招新关闭与 AI 故障隔离；AI 测试用本地协议服务提供人工向量，验证余弦排序、输入去重/缓存复用、内容与版本更新、维度异常及模型失败。人工向量不是实际语义效果证据。
- `npm run build`、`npm run test:prototype`、`npm run test:business`：类型、构建、原型模式及模拟 HTTP/SSR；覆盖新描述表单、错误提示、结果链接与分数说明、兴趣改变清理、跨账号迟到响应。
- `node scripts/check-interest-recommendation.mjs`：LOCAL 模式真实 HTTP，创建并保留单独虚构学生，不修改旧资料；检验描述保存、超长拒绝、Session 隔离、未配置 503 与基础查询。
- 重启校园服务后运行 `node scripts/check-interest-recommendation.mjs restored`：重读同一记录。仅 AI 停机时运行 `node scripts/check-interest-recommendation.mjs offline`：实际验证描述仍可保存、推荐失败但社团可查。

实际模型、手机界面与推荐效果属于单独验收。后续固定一组学生描述与人工相关社团，再对比标签/关键词、纯语义与规则融合，记录 Top-K 相关性及失败情况；不能用本地协议向量测试代替论文中的真实推荐实验。

## 2026-10-08 实际验证记录

- 全工程 `mvn -DskipTests=false test` 通过：校园 55 项、AI 17 项，共 72 项，失败/错误/跳过均为 0。新增 13 项推荐相关测试。此前完整 `package` 通过；最后的社团文本更新保护另通过校园打包测试。
- 前端构建通过（99 个模块）；原型 219 个断言、28 条 SSR 路由通过；业务模拟 HTTP/SSR 回归通过，包括语义结果、模型未配置提示与跨账号迟到响应。
- 真实 Vite 代理与运行进程下，`check-interest-recommendation.mjs` flow、offline、restored 通过；offline 实际停止且随后恢复本项目 AI 服务，校园资料保存与社团查询仍可用。restored 重启校园服务后读回同一虚构学生资料。
- `smoke.mjs` 验证两个服务、知识上传、LOCAL 关键词问答与历史通过；未调用收费模型。默认文件数据库升级前已复制闭库备份到忽略的 `.run/`，没有修改旧学生或负责人资料。
- 未完成真实 Embedding 服务与推荐质量实验，也未完成本轮真实浏览器点击或手机截图验收。模型接口测试的人工向量不代表已验证真实中文理解能力。
