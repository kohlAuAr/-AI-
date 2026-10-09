# 第三方代码说明

本项目 AI 服务中的部分实现根据本地 PaiSmart 源码进行精简改造。上游项目为 PaiSmart，项目地址：https://github.com/itwanger/PaiSmart 。本地来源为 `C:/workspace/PaiSmart`，原仓库 remote 为 `git@gitcode.com:javabetter/PaiSmart.git`。

上游根目录 LICENSE 为 Apache License 2.0，完整文本保存在 `licenses/PaiSmart-APACHE-2.0.txt`。没有复制上游前端代码；本项目的 Vue 页面独立编写。

| 上游来源 | 本项目位置 | 改造内容 |
|---|---|---|
| `service/ParseService.java` | `ai-service/.../knowledge/TextChunker.java` | 保留段落、句子分块处理，移除 PDF/OCR/HanLP，使用简单字符重叠 |
| `client/EmbeddingClient.java` | `ai-service/.../client/ModelClient.java` | 保留有界分批处理思路与索引顺序校验；移除商业额度和动态供应商数据库依赖；2026-10-09 将手写 Embedding HTTP 接入替换为 Spring AI 模型 API |
| `client/DeepSeekClient.java` | `ai-service/.../client/ModelClient.java` | 精简为普通问答请求；暂不包含流式 WebSocket |
| `service/HybridSearchService.java`、`entity/SearchResult.java` | `ai-service/.../knowledge/HybridSearchService.java` | 沿用关键词、向量与引用输出的组织方式；小规模精确扫描替代 Elasticsearch，删除企业组织标签耦合 |
| `service/ConversationService.java`、`service/ChatHandler.java` | `ai-service/.../chat/` | 沿用持久历史、短期上下文和引用持久化的职责划分；移除生成任务、扣费与复杂流式状态 |

源码中的改动说明与本文件一起保留。未复制原数据库、上传资料、模型密钥或运行配置。ReAct、MCP、DAG 尚未迁入当前骨架。

AI 模块通过 Maven 依赖 Spring AI 1.0.9 的 `spring-ai-ollama`、`spring-ai-openai`，项目地址 https://github.com/spring-projects/spring-ai ，上游许可 Apache License 2.0。本项目调用其公开 API，未复制其源码文件。采用框架接入不撤销上表中仍保留的 PaiSmart 改造来源。
