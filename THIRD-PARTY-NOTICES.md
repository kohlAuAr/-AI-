# 第三方代码说明

本项目 AI 服务中的部分实现根据本地 PaiSmart 源码进行精简改造。上游项目为 PaiSmart，项目地址：https://github.com/itwanger/PaiSmart 。本地来源为 `C:/workspace/PaiSmart`，原仓库 remote 为 `git@gitcode.com:javabetter/PaiSmart.git`。

上游根目录 LICENSE 为 Apache License 2.0，完整文本保存在 `licenses/PaiSmart-APACHE-2.0.txt`。2026-10-18 起按用户要求改造复用上游前端的布局、主题、登录卡片、聊天输入区和会话列表；本地 `frontend/package.json` 同时声明 `license: MIT`、author 为 PaiSmart，此处保留其声明而不删除根许可证或来源。校园请求、Session/CSRF、权限及现有业务状态仍由本项目维护。

| 上游来源 | 本项目位置 | 改造内容 |
|---|---|---|
| `service/ParseService.java` | `ai-service/.../knowledge/TextChunker.java` | 保留段落、句子分块处理，移除 PDF/OCR/HanLP，使用简单字符重叠 |
| `client/EmbeddingClient.java` | `ai-service/.../client/ModelClient.java` | 保留有界分批处理思路与索引顺序校验；移除商业额度和动态供应商数据库依赖；2026-10-09 将手写 Embedding HTTP 接入替换为 Spring AI 模型 API |
| `client/DeepSeekClient.java` | `ai-service/.../client/ModelClient.java` | 原精简问答接入在 2026-10-09 替换为 Spring AI ChatModel，保留普通同步问答边界；不包含流式 WebSocket |
| `service/HybridSearchService.java`、`entity/SearchResult.java` | `ai-service/.../knowledge/HybridSearchService.java` | 沿用关键词、向量与引用输出的组织方式；小规模精确扫描替代 Elasticsearch，删除企业组织标签耦合 |
| `service/ConversationService.java`、`service/ChatHandler.java` | `ai-service/.../chat/` | 沿用持久历史、短期上下文和引用持久化的职责划分；移除生成任务、扣费与复杂流式状态 |

源码中的改动说明与本文件一起保留。未复制原数据库、上传资料、模型密钥或运行配置。当前受限只读 ReAct 是校园项目自己的适配实现；没有搬入 PaiSmart 的收费、WebSocket 生成任务或外部 MCP/DAG。

## 前端改造来源

| PaiSmart 本地来源 | 本项目位置 | 改动边界 |
|---|---|---|
| `frontend/packages/materials/src/libs/admin-layout/`、`src/layouts/base-layout/` | `frontend/src/paismart/AdminLayout.vue` | 提取 header/sider/content 插槽、收起侧栏和移动抽屉；去掉未使用的多种布局、页签和主题设置中心，用普通 CSS 替代 UnoCSS 工具类 |
| `frontend/src/layouts/modules/global-header/`、`global-menu/modules/vertical-menu.vue` | `frontend/src/paismart/PaiSmartShell.vue` | 保留 Naive UI 菜单、折叠尺寸/图标/缩进配置、顶部账号区；替换为校园路由和真实角色范围 |
| `frontend/src/theme/settings.ts` | `frontend/src/paismart/theme.ts`、`campus-pages.css` | 沿用 #646cff、白色容器、#f7fafc 布局底色及卡片/控件视觉，适配现有校园页面 |
| `frontend/src/views/_builtin/login/index.vue` | `frontend/src/prototype/LoginView.vue` | 改造卡片、品牌头部和表单结构；保留校园注册/登录，不复制明文密码记忆、微信绑定或未实现的找回密码 |
| `frontend/src/views/chat/index.vue`、`modules/input-box.vue`、`modules/conversation-sidebar.vue` | `frontend/src/community/CommunityConversation.vue`、`frontend/src/paismart/InputBox.vue`、`ConversationSidebar.vue` | 提取会话侧栏、消息区、输入框及输入框阴影样式，改接已有校园 HTTP 与本人历史；未实现的归档、充值、流式停止和反馈按钮不迁入 |

Naive UI 使用与本地 PaiSmart 相同的 2.41.0 版本，通过 npm 依赖引入；许可证 MIT，文本保存在 `licenses/Naive-UI-MIT.txt`。自动测试使用 `@css-render/vue3-ssr` 的正式样式适配器渲染真实组件，不使用伪造组件替身。

AI 模块通过 Maven 依赖 Spring AI 1.0.9 的 `spring-ai-ollama`、`spring-ai-openai`，项目地址 https://github.com/spring-projects/spring-ai ，上游许可 Apache License 2.0。本项目调用其公开 API，未复制其源码文件。采用框架接入不撤销上表中仍保留的 PaiSmart 改造来源。
