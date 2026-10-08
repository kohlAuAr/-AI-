# 纯前端公网预览

2026-10-01 本次可用的临时地址：

https://spotlight-powered-disk-sen.trycloudflare.com

## 发布范围

- 只提供 `public-prototype/dist` 中构建后的 HTML、CSS、JavaScript。
- 独立入口 `frontend/src/public-main.ts` 不导入后端联调页或 API 客户端。
- 公网版采用 hash 路由，刷新详情页不需要服务端路由。
- 负责人审核、成员关系、活动发布与报名均为浏览器本地模拟记录，不接数据库。
- 不上传后端、密钥、用户数据库、知识资料或聊天记录；不提供资料上传、问答接口。
- 静态服务器只允许首页及构建后的 JS/CSS 文件，并通过 `connect-src 'none'` 禁止页面发出网络 API 请求。

## 已核对

- 前端类型检查和公网专用构建通过；产物 JavaScript 语法检查通过。
- 公网首页返回 HTTP 200，网页标题包含“社遇”。
- 首页引用的 JS 和 CSS 均返回 HTTP 200，媒体类型正确。
- `/api/system`、`/.env`、`/src/main.ts` 返回 HTTP 404。
- 静态服务仅监听 `127.0.0.1:5180`；隧道指向该服务，不指向 Vite、Spring Boot 或整个工作目录。
- 未进行手机真机和浏览器端到端点击验收；HTTP 检查不等于交互实测。

## 临时链接限制

这是 Cloudflare Quick Tunnel，不是永久静态托管。电脑和静态服务/隧道进程须保持运行；关闭进程或电脑后链接失效。重新创建隧道会产生不同地址。任何持有链接的人都可查看公开原型；不同访问者的演示数据互不共享。

参考：https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/

本次进程记录在忽略的 `.run/public-preview-processes.json`，日志为 `.run/public-static*.log` 和 `.run/public-tunnel*.log`。停止时须先核对记录 PID 的命令行仍对应本项目，再停止，防止误停复用 PID 的其他进程。

## 长期托管状态

Sites 长期托管站点已登记，身份保存在 `public-prototype/.openai/hosting.json`。源代码上传连续三次因网络连接重置失败，尚未保存版本或完成部署。该站点的预期地址不可当作已发布链接。后续必须复用同一站点身份，不能重复创建站点。

长期托管恢复后可以取消临时隧道；本次没有声称永久部署成功。
