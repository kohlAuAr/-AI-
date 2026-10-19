# PDF 资料上传与检索

## 使用

平台管理员在“资料知识库”上传公开资料；社团负责人上传所属社团资料。学生只查看其可见资料，不增加上传权限。选择文字版 PDF，点击“上传并处理”，完成后点击“查看”核对提取出的正文，再从“资料问答”提问并展开出处。

- PDF：最大 5MB、50 页，须包含可提取文字。不支持纯图片/扫描件、需要密码或其他加密的文件。
- Markdown/TXT：仍要求 UTF-8，最大 128KB。
- 提取后的正文统一限制为 UTF-8 128KB，最多 128 个片段；超出应拆成几份资料，不静默截断。
- 损坏、伪装后缀或没有可提取正文的 PDF 会报错。混合图文 PDF 只索引文字，图片、图表、附件、手写内容不识别；复杂表格、多栏、特殊字体的顺序或文字可能失真，须核对提取正文。
- 原 PDF 不持久保存，也不提供原文件下载或 PDF 在线预览；数据库保存提取正文、片段、向量和原文件名，引用仍是文档/片段，不是 PDF 页码。

## 处理链与边界

浏览器 Session/CSRF → 校园服务校验当前账号的 PUBLIC/CLUB 权限和文件大小 → 服务鉴权转发 → AI 服务复查上传权限 → 校验类型/字节大小/PDF 头、解析并检查加密/页数 → 限长提取正文 → 原有分块、内容及模型版本去重 → Embedding → 事务保存。

使用 [Apache PDFBox 3.0.8](https://pdfbox.apache.org/download.html)，通过 [PDFBox 3 的 Loader](https://pdfbox.apache.org/3.0/migration.html) 和 PDFTextStripper 提取文本。排除其 commons-logging，复用 Spring 的日志桥。没有调用 PDF 的脚本、打开外链、提取附件、渲染图片或引入 Tika/OCR/消息队列。

大小、页数与正文上限用于约束普通资料处理，不是对恶意 PDF 的完整隔离。当前同步解析没有独立进程的 CPU/内存/硬超时沙箱，不能宣称已具备公开互联网任意文档处理的安全性；正式上线应按 [PDFBox 安全指引](https://pdfbox.apache.org/security.html) 增加隔离和容量验证。已上传的资料不会在启动时自动重新解析或重建向量。

解析失败在模型调用之前返回；Embedding 失败不保存半篇资料。模型启用时提取的文字仍会发送给管理员配置的 Embedding 服务，没有额外第三方 PDF 转换平台。校园服务与 AI 服务 multipart 都是单文件 5MB、整次请求 6MB；海报仍由自己的接口限制为 2MB。将来添加反向代理时，应为知识上传路径配置至少 6MB 的请求体上限，不能只改浏览器 accept。

## 验证

- `mvn -DskipTests=false test` 包含 PDF 提取（含压缩小文件膨胀和 UTF-8 字节数）、格式/扫描/加密/页数限制、PDF 入库去重和带引用问答、社团权限和失败原子性测试。模型协议部分使用桩，不等于真实模型质量证明。
- `npm run test:knowledge` 检查前端类型、大小、空文件、辅助说明、权限文案和 SSR；另运行 `test:business`、`test:prototype` 与 `build`。
- `node scripts/check-pdf-knowledge.mjs flow <虚构PDF目录>` 仅接受当前原生本地 Ollama，使用目录内 `campus-guide.pdf`、`club-guide.pdf`、`image-only.pdf`、`too-many-pages.pdf`；保留独立测试账号、公开/社团资料与回答，不改旧资料。已存在证明时拒绝覆盖，改用 `node scripts/check-pdf-knowledge.mjs restored` 检查同一批数据和权限。证明存于忽略目录 `.run/pdf-knowledge-proof.json`。不使用真实收费云端，不下载或切换模型。

### 2026-10-19 本机记录

完整 Maven 回归：校园服务 82 项（2 项可选 Redis 实机测试跳过），AI 服务 60 项（1 项可选 Redis 实机测试跳过），0 失败、0 错误。前端知识库专项、业务、原型（228 断言、29 路径）及构建通过，现有 JS 主包体积警告保留。本机后端均重新打包，关闭数据库备份后重启；原有海报 fixture 和 RAG 资料/历史的 restored 检查通过。

真实 HTTP 已验证图片 PDF、超过 50 页、伪装文件、PDF 超过 5MB 和文本超过 128KB 均被拒绝；浏览器实际选择并提交图片 PDF，正确提示无法提取正文，375px 与 812px 视口未出现页面横向溢出。真实中文文字版 PDF 的 Embedding/问答验收暂未完成：本机 Ollama 11434 未监听，上传返回明确的 503，而不是假成功。当前未生成 `.run/pdf-knowledge-proof.json`；待用户开启原有 Ollama 后运行 flow，再重启 AI 并运行 restored。
