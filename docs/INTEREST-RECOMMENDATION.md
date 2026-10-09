# 自由文本兴趣与社团混合推荐

## 使用与边界

登录 → 我的 `/me` → 编辑资料 → 填写兴趣描述 → 保存 → 助手 `/assistant` → 按我的兴趣推荐。

例如：“喜欢用手机记录校园生活，想学拍照和剪视频，零基础，希望社团氛围轻松一点。”描述最多 1000 字，标签仅辅助展示，不参与当前语义分数。只选标签而没有描述时不生成推荐。

每个社团的名称、类别、简介和标签拼成完整文本，不是只比较名字。学生描述与该文本用同一 Embedding 模型编码，同时用 BM25 计算文字相关性，最后以 RRF 融合两份排名，最多展示 3 个招新社团。当前没有经验证的质量阈值；Top-3 是候选参考，不是适合率或录取概率。展示的“匹配参考”是数据库社团简介，不是大模型生成的个性化解释。

校园服务在模型调用前过滤招新关闭状态与已识别的验收社团，返回前重新检查。文字形式的招新条件、空闲时间无法自动判断资格或课表冲突，页面提醒本人核对。未实现行为画像、LLM 解释、ReAct、MCP、DAG，也没有自动报名。

## BM25 与语义排名如何融合

1. 校园后端读取当前账号兴趣和当前可推荐社团；候选文本包含名称、类别、简介、标签，不把时间和招新条件当作可自动判断的兴趣条件。
2. Spring AI 调用 Ollama，计算兴趣与各候选的余弦相似度，形成语义排名。已缓存且文本未变的向量继续复用。
3. `Bm25` 针对同一批完整文本计算词频、文档频率与长度归一化，参数 `k1=1.2`、`b=0.75`。中文采用连续汉字的二元切片，如“编程”“摄影”；英文转小写后按词处理。不是专业中文分词器，也不包含词义或否定识别。
4. 使用 RRF：对每个正分候选，按所在排名累加 `1 / (60 + rank)`。未命中的 BM25 候选不获得文字检索贡献；相同分数共享名次。融合分相同，再按 BM25、语义分和社团 ID 决定稳定顺序。

BM25 与余弦分数的量纲不同，不直接相加。公式与参数参考 [BM25 算法说明](https://www.elastic.co/blog/practical-bm25-part-2-the-bm25-algorithm-and-its-variables) 和 [RRF 官方说明](https://www.elastic.co/docs/reference/elasticsearch/rest-apis/reciprocal-rank-fusion)；本项目自行按公式计算，不调用 Elasticsearch，也没有新建检索服务器。

接口方法标记为 `HYBRID_BM25_VECTOR_RRF`。`score` 仍是原始余弦相似度，新增 `bm25Score` 为文字检索分，`fusionScore` 为排序分；三个数均不是概率。页面明确“按混合检索排序”，因此结果的语义分不一定从高到低。BM25 统计每次根据当前候选重算，不缓存旧排名；社团简介修改后，文字检索与对应向量都使用新文本。

多兴趣文本可让“编程”和“摄影”分别产生文字命中，缓解整段向量偏向其中一种兴趣的问题，但尚未实现兴趣拆分或覆盖率约束，不能保证任意多个兴趣都出现在前三名。模型不可用时仍明确失败，不偷偷以 BM25 单独结果冒充混合推荐。

## 验收数据隔离

只排除 `check-campus-backend.mjs` 的明确约定：名称完整匹配 `校园后端联调社（虚构）backend_[a-z0-9]+`，且简介恰为“仅用于本地接口验收，不是真实学校社团。”。已有两个验收社团和以后由同一脚本生成的同类记录均不会进入推荐或 BM25 统计。

不删除、不关闭这些社团，也不改成员、活动、签到、经费或通知；原 API 仍可查询它们并运行 restored 验收。普通负责人创建的社团、带“测试”二字的其他名称及正常 demo 社团不会因这一约定被排除。这是当前本地工程的窄范围验收隔离，不是生产环境数据隔离制度；若未来增加多类测试数据，应独立测试库，不继续堆名字过滤规则。

## 数据与服务

- `campus_account.interest_description` 保存原文；原账号新增列可为空，读出为空字符串。PUT `/api/profile` 可清空描述，旧客户端省略该字段时保留已保存描述。
- POST `/api/ai/recommendations` 要求 Session / CSRF，校园服务读取当前账号，不采信请求中的 userId、兴趣文本或候选社团。
- 校园服务仅向 POST `/internal/recommendations` 发送兴趣描述与公共社团文本，不发送姓名、账号、密码、专业等身份信息。
- AI 的 `interest_vector` 保存派生向量，不保存兴趣原文或账号编号。缓存主键为 SHA-256（编码流程版本 + Embedding 地址/模型版本 + 文本）；文本改变或模型地址/名称改变后，不使用旧向量。向量本身仍是敏感派生数据，不等于彻底匿名。
- 缓存保存在 AI 数据库，模型调用不持有跨网络数据库事务。当前小数据版本串行填充缓存、全量余弦扫描及内存 BM25；未做高并发压测、缓存淘汰或大型 ANN 索引，不声称高并发能力。
- 个人资料保存、社团查询、招新申请均不调用模型。AI 停机、模型失败或未配置时，只有推荐返回明确失败；没有标签/模拟结果冒充语义推荐。

源码：校园 `identity/ProfileService`、`ai/RecommendationController` → `AiServiceClient` → AI `recommendation/RecommendationController`、`RecommendationService`、`InterestVectorRepository` → `client/ModelClient.embed()` → Spring AI `OllamaEmbeddingModel` / `OpenAiEmbeddingModel`。前端 `CommunityProfileEditor` → `business.saveProfile()`；`CommunityAssistantView` → `recommendations.loadRecommendations()`。切换账号或修改兴趣清理旧推荐，迟到响应不能覆盖新账号结果。

## 配置真实模型

聊天与推荐配置分开：`AI_MODE` 决定问答模式；`EMBEDDING_PROVIDER` 决定向量接入方式，`auto` 跟随模式（LOCAL 关闭向量、OPENAI 使用兼容接口、OLLAMA 使用原生接口），`none` 显式关闭，`ollama` 使用本机原生接口，`openai` 使用兼容接口。还需要非空 `EMBEDDING_MODEL`。只运行推荐不需要配置 CHAT_MODEL，也不调用聊天生成接口。

本机配置：`AI_MODE=local`、`EMBEDDING_PROVIDER=ollama`、`EMBEDDING_BASE_URL=http://127.0.0.1:11434`、`EMBEDDING_MODEL=qwen3-embedding:0.6b`、`EMBEDDING_API_KEY=`。先手动打开已有 Ollama。原生接口不带 `/v1`，请求 `/api/embed`，不自动拉取模型。若改用兼容接口，设置 `EMBEDDING_PROVIDER=openai`，Base URL 使用服务提供的兼容根路径（通常带 `/v1`），请求 `/embeddings`。

LOCAL + Ollama 只为兴趣推荐编码，知识上传仍保存关键词分块、版本为 `local-keyword`，问答行为不变。需要真实 RAG 时，配置聊天模型并启用 OLLAMA 或 OPENAI 问答模式，重新上传资料；见 [RAG 问答说明](RAG-WORKFLOW.md)。模型必须支持当前原文编码方式和中文语义匹配；需要 query/document 专用前缀的模型，应先明确其编码要求再适配。本轮使用同一模型直接编码两种文本，尚未做模型专用任务前缀实验。

`/api/ai/status` 的 recommendation 状态只表示配置，不代表健康检查。Spring AI 迁移使用新的缓存命名空间（适配器版本 + 接入方式 + 地址 + 模型），不复用旧手写客户端的向量。模型权重如果原地替换却保留同一地址和名称，需更换版本化模型名称或在停机备份后清理对应缓存；当前不做权重自动探测。

不用把模型 Key 放到前端，不要求学生充值。学校自建模型或统一采购接口均由服务端配置。本轮调用已安装的本机模型，没有下载模型或调用收费服务；使用本机计算资源并不代表生产部署无成本。

## 验证方法

- `mvn -DskipTests=false test` / `package`：校园推荐测试覆盖身份、CSRF、描述持久化、旧请求兼容、长度限制、招新关闭与 AI 故障隔离；AI 测试用本地协议服务提供人工向量，验证余弦排序、输入去重/缓存复用、内容与版本更新、维度异常及模型失败。人工向量不是实际语义效果证据。
- `npm run build`、`npm run test:prototype`、`npm run test:business`：类型、构建、原型模式及模拟 HTTP/SSR；覆盖新描述表单、错误提示、结果链接与分数说明、兴趣改变清理、跨账号迟到响应。
- `node scripts/check-interest-recommendation.mjs`：LOCAL 且 Embedding 未配置时的真实 HTTP，创建并保留单独虚构学生，不修改旧资料；检验描述保存、超长拒绝、Session 隔离、未配置 503 与基础查询。已配置模型时在写资料前退出，避免误用。
- 重启校园服务后运行 `node scripts/check-interest-recommendation.mjs restored`：重读同一记录。仅 AI 停机时运行 `node scripts/check-interest-recommendation.mjs offline`：实际验证描述仍可保存、推荐失败但社团可查。
- `node scripts/check-ollama-recommendation.mjs`：LOCAL 问答 + OLLAMA 推荐，通过 Vite、Session/CSRF、校园读取资料与社团、AI 编码和排序的完整 HTTP 链。`restored` 重读同一虚构账号并再次推荐；`offline` 要求只停止项目 AI 服务，保存资料和查社团仍成功。脚本不停止 Ollama、不改旧账号。报告保存在忽略的 `.run/`。

实际模型、手机界面与推荐效果属于单独验收。后续固定一组学生描述与人工相关社团，再对比标签/关键词、纯语义与规则融合，记录 Top-K 相关性及失败情况；不能用本地协议向量测试代替论文中的真实推荐实验。

## 2026-10-08 实际验证记录

- 全工程 `mvn -DskipTests=false test` 通过：校园 55 项、AI 17 项，共 72 项，失败/错误/跳过均为 0。新增 13 项推荐相关测试。此前完整 `package` 通过；最后的社团文本更新保护另通过校园打包测试。
- 前端构建通过（99 个模块）；原型 219 个断言、28 条 SSR 路由通过；业务模拟 HTTP/SSR 回归通过，包括语义结果、模型未配置提示与跨账号迟到响应。
- 真实 Vite 代理与运行进程下，`check-interest-recommendation.mjs` flow、offline、restored 通过；offline 实际停止且随后恢复本项目 AI 服务，校园资料保存与社团查询仍可用。restored 重启校园服务后读回同一虚构学生资料。
- `smoke.mjs` 验证两个服务、知识上传、LOCAL 关键词问答与历史通过；未调用收费模型。默认文件数据库升级前已复制闭库备份到忽略的 `.run/`，没有修改旧学生或负责人资料。
- 未完成真实 Embedding 服务与推荐质量实验，也未完成本轮真实浏览器点击或手机截图验收。模型接口测试的人工向量不代表已验证真实中文理解能力。

## 2026-10-09 Spring AI 与 Ollama 实际验证

- AI 模块引入 Spring AI 1.0.9 BOM 与 Ollama / OpenAI 模型库，保留 Spring Boot 3.4.2，不启用 starters 自动配置或拉取模型。兼容性依据 [Spring AI 1.0 入门文档](https://docs.spring.io/spring-ai/reference/1.0/getting-started.html)，原生接口依据 [Ollama Embedding 文档](https://docs.spring.io/spring-ai/reference/1.0/api/embeddings/ollama-embeddings.html)。
- 已安装的 `qwen3-embedding:0.6b` 实际返回 1024 维向量。经当前数据库招新过滤后的三个测试兴趣，首位结果如下。分数是原始余弦相似度，不是准确率、适合概率或百分比；现有虚构联调社团也会参与排名，未为验收删数据或限定候选。

| 测试描述 | 首位社团 | 余弦相似度 |
|---|---|---|
| 喜欢用手机记录校园生活，想学拍照和剪视频，零基础 | 摄影社（示例） | 0.4992 |
| 对算法和写代码感兴趣，希望有人一起刷编程题和做小项目 | 程序设计社（示例） | 0.5514 |
| 喜欢在山野里走路探索自然，周末想和大家一起去户外徒步 | 山野户外社 | 0.6363 |

- 真实模型 flow 与 AI 重启后的 restored 通过，重复请求结果一致。缓存不重复编码、仅更新改变文本、版本隔离由本地协议服务记录调用次数验证；重复请求耗时不能独自证明缓存命中。
- 实际停止本项目 AI 服务时，offline 验证同一虚构账号资料保存/读取与社团查询成功，推荐返回 503；随后恢复 AI，校园与前端进程未重启，用户的 Ollama 未停止。
- `mvn -DskipTests=false package`：校园 55 项、AI 23 项，共 78 项，失败/错误/跳过为 0。新增原生 `/api/embed`、32 条分批、无 Key/无下载、无效响应/向量、LOCAL 推荐与知识问答隔离、原生错误不入缓存测试。测试中的协议向量仍是人工数据。
- 前端构建、业务模拟 HTTP/SSR、原型 219 个断言及 28 条 SSR 路由通过；`smoke.mjs` 的 LOCAL 上传、去重、摘录、引用和历史回归通过。本轮不改页面，不宣称完成手机真机点击验收。
- 两个默认 H2 文件在启动前已闭库复制备份；只新增独立虚构验收账号和派生向量，未修改现有学生资料、原 PaiSmart 或已保存的原型。

以上是当日首次接入的历史记录，后续混合推荐与隔离结果见下节；不是正式推荐质量实验、生产并发保障、LLM 问答迁移或 Agent。后续需要人工标注更多描述与相关社团，覆盖无关兴趣、否定表达和模糊描述，再评估 Top-K。

## 2026-10-09 混合推荐与验收隔离验证

- 用户反馈“喜欢编程和摄影”未展示程序设计社。用同一句话和现有招新数据复算，纯语义排名为摄影社、两个验收社团、程序设计社，后者在第四名被 Top-3 截断；并非只取名字或招新关闭。
- 新版同时排除明确验收候选，并加入 BM25 + RRF。实际通过 Vite / Session / CSRF / 校园数据库 / Spring AI / Ollama 的结果，前两名如下：

| 排名 | 社团 | 余弦相似度 | BM25 分 | RRF 排序分 |
|---|---|---|---|---|
| 1 | 程序设计社（示例） | 0.4179 | 1.9339 | 0.032522 |
| 2 | 摄影社（示例） | 0.5393 | 1.4133 | 0.032522 |

两者 RRF 分相同，按 BM25 分打破平局；不是人为固定这两个社团的顺序。第三名为山野户外社。该例不证明所有多兴趣句子都得到均衡结果。

- 四个真实中文样例（摄影、编程、户外、编程+摄影）通过，重复结果一致；样例报告保存在 `.run/ollama-recommendation-flow.json`，不提交账号凭据与运行数据。
- `mvn -DskipTests=false test` 与 `package` 通过：校园 57 项、AI 30 项，共 87 项，失败/错误/跳过为 0。新增测试覆盖 BM25 公式、中文简介命中、英文大小写、空语料/未命中、多兴趣融合、无文字命中时保留语义排序、零语义分但文字命中、平局稳定性、只排除验收候选、拒绝异常混合分。原有模型失败、缓存更新、招新关闭及兴趣账号隔离测试仍通过。
- `npm run build`、业务模拟 HTTP/SSR、原型 219 个断言和 28 条 SSR 路由通过。助手说明改为混合检索，保留原界面与原始语义分，新增“文字线索命中”提示，不把 RRF 排序分称为相似度或概率。本轮未做真实手机点击或视觉验收。
- 原 `check-campus-backend.mjs restored` 通过：验收社团 97 的成员、活动 129、签到、收藏、经费支出和取消通知均保留；隔离没有关闭招新或删除记录。两个后端升级前均闭库备份，未修改原学生资料或原 PaiSmart。
- 实际 AI 停机时 `check-ollama-recommendation.mjs offline` 通过，个人资料保存、读取和社团查询不受影响，推荐返回 503；随后恢复 AI，用户的 Ollama 未停止。
