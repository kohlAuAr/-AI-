# 账号与个人资料链

## 使用

正常开发模式打开 `http://127.0.0.1:5178/login`，选择“注册学生账号”。账号使用 3–32 位小写字母、数字或下划线，密码至少 8 位、UTF-8 编码最多 72 字节；填写姓名和专业，重复账号会被拒绝。创建成功后回到登录表单，不假装已经登录。

登录后进入 `/me`，点击“编辑资料”。账号只读；姓名、专业必填；兴趣、空闲时间可不填或清空。保存成功会更新个人页和顶部姓名。退出后重新登录，或刷新页面，资料由后端重新读取。助手页使用当前账号兴趣筛选共同标签，不读取本浏览器的模拟兴趣，不声称是 AI 推荐。空闲时间为文字备注，不自动计算课表冲突。

自助注册只能创建 STUDENT，不能选择社团负责人或平台管理员权限。请求采用既有 Session / CSRF，没有修改后端认证、数据库结构或 AI 服务。纯前端原型仍没有注册服务，保留浏览器本地兴趣；已保存的原型归档不变。收藏仍未接账号接口。

## 源码路线

- `frontend/src/prototype/LoginView.vue`：注册和登录表单，密码确认、加载状态与错误反馈。
- `frontend/src/prototype/business.ts`：注册、读取/保存个人资料，退出清理与异步响应账号校验。
- `frontend/src/community/CommunityProfileEditor.vue`：姓名、专业、兴趣和空闲时间编辑。
- `CommunityPersonalView.vue`、`CommunityAssistantView.vue`：展示账号资料和标签筛选。
- `campus-service/.../identity/ProfileController.java` → `ProfileService.java` → `AccountRepository.java`：既有注册、本人读取和更新接口。

## 2026-10-08 验收

- `npm run build`：类型检查、Vite 构建通过。
- `npm run test:prototype`：219 个断言、28 条渲染路由通过；纯前端原型仍不调用业务服务。
- `npm run test:business`：模拟 HTTP 与 SSR 回归通过，包括注册入口、资料编辑表单、CSRF、账号兴趣读取、保存失败不覆盖成功资料、退出清理、重新登录和迟到响应不覆盖新账号。
- `node scripts/check-account-profile.mjs`：通过实际 Vite 代理访问运行中的校园服务。验证未登录拒绝、非法/重复注册、强制学生角色、资料保存/清空、无效保存不覆盖、退出、全新 Session 重读及账号隔离；保留独立虚构学生 `profile_muz82ucd`（id 129）。不修改旧学生或负责人资料。
- `node scripts/smoke.mjs`：前端代理、两个服务、公开资料上传、本地关键词问答与历史检查通过；没有调用付费模型。

本轮浏览器自动检查被安全策略阻止，未完成真实浏览器点击、手机截图或布局验收。HTTP 全新 Session 重读不等于本轮重新验证过进程重启后的持久化；上述验收不冒充这两类证据。

## 下一步

补齐负责人创建/编辑所属社团、招新条件与招新开关页面，再补活动后续管理；不因这一条链自动扩展找回密码、学校统一身份认证或语义推荐。
