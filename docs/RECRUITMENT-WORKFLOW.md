# 第一条真实业务：申请加入社团

日期：2026-10-04。沿用保存的社遇设计，完成入社业务，不扩展活动报名或 AI 编排。

## 你现在可以做什么

学生登录 → 从数据库查看社团 → 提交理由 → 我的页面查看待审核 → 对应社团负责人登录审核 → 成员名单出现学生 → 学生重新登录看到结果和已加入社团。

这不是浏览器模拟：申请和成员关系保存在校园数据库。默认数据库是项目已有的 H2 文件库，不需要先安装 MySQL/Redis。服务重启后记录保留，但登录 Session 会失效，需要重新登录。所有账号和社团资料仍是虚构测试数据。

## 启动

在项目根目录打开 PowerShell：

```powershell
cd C:\workspace\campus-club-platform
mvn -DskipTests=false package
cd frontend
npm ci
cd ..
powershell -ExecutionPolicy Bypass -File scripts\start-dev.ps1
```

如果已经通过脚本启动，先执行 `scripts\stop-dev.ps1` 再重新打包和启动。若服务由 IDEA 启动，在 IDEA 中停止本项目实例，不要重复占用端口。

打开 `http://127.0.0.1:5178/login`。start-dev 启动三个开发进程用于兼容原有资料联调，但入社流程本身只需要校园服务与前端，不依赖 AI 服务。

也可以在 IDEA 打开根 pom，启动 `CampusApplication`；前端在 frontend 执行 `npm run dev`。不启动 AI 服务仍可完成入社。

## 演示账号与点击路线

仅默认 demo 配置会初始化以下公开账号，密码均为 `CampusDemo123!`：

| 账号 | 身份 | 权限 |
|---|---|---|
| student | 林同学 | 提交、查看和撤回自己的申请 |
| student2 | 陈同学 | 用于检查不同学生记录隔离 |
| photo_manager | 摄影社负责人 | 仅管理摄影社申请与成员 |
| code_manager | 程序设计协会负责人 | 仅管理程序设计协会，用于验证跨社团越权 |

1. 学生登录，打开“社团”，进入摄影社详情。已有 H2 数据的摄影社可能仍名为“摄影社（示例）”，这是保留旧数据，并非加载错了。
2. 填写理由、确认提交。在“我的 → 入社申请”看到待审核。
3. 点退出，以 photo_manager 登录。进入“招新审核”，查看并审核。
4. 通过后进入“成员管理”查看学生。列表包含负责人，人数也从数据库统计。
5. 退出，再以同一学生账号登录，查看审核意见与“我的社团”。

需要再次演示时可以换另一个学生/社团。已加入同一社团的学生不能重复申请；不要为了重复演示删除数据库。拒绝或撤回后可重新申请，旧记录仍保留。

## 从哪几个文件开始看源码

先跟一件事，不按菜单数量学习：

1. `frontend/src/prototype/DetailView.vue`：学生点击提交，调用 sendApplication。
2. `frontend/src/prototype/business.ts`：把页面社团的 backendId 和 reason 发给后端；不传可伪造的学生身份。
3. `frontend/src/api.ts`：带浏览器 Session Cookie，写操作先取 CSRF token。
4. `identity/SecurityConfig.java`、`IdentityService.java`：登录确认是谁；权限不靠前端按钮。
5. `recruitment/RecruitmentController.java`：接收 clubId/reason 并校验长度。
6. `recruitment/RecruitmentService.java`：查招新状态、重复申请和成员，保存 pending；审核时检查管理范围。
7. `ApplicationRepository` 与 `MembershipRepository`：数据库查询、唯一约束、申请锁；批准与成员写入同一事务。
8. `BusinessPersonalView.vue` 与 `BusinessManageView.vue`：重新查后端记录，分别展示学生和负责人看到的结果。

后端文件路径前缀是 `campus-service/src/main/java/com/campus/business/`。详细接口见 `API.md`。

## 规则与失败情况

- 未登录不能查看私人记录或提交申请；不同学生只能查看自己的记录。
- 负责人不能审核其他社团，也不能用负责人账号替学生申请。
- 招新结束、已有待审核申请、已经是成员时不能再次申请。
- 申请理由非空且最多 500 字；审核意见最多 300 字，approved 必填。
- 只有 pending 能审核或由本人撤回；重复审核不能更改旧结果。
- 审核状态与成员关系一起提交，任何一项失败都回滚。
- 失败时不会假装保存成功，也不会切回模拟申请。

## 与原型、其他功能的边界

纯前端原型仍通过 `start-prototype.ps1` 启动，业务接口开关为 false；不调用后端。2026-10-01 的原型备份保持不动。不要同时占用 5178。

当前活动报名、收藏、消息仍使用浏览器模拟。正常开发工作台里的正式活动与经费显示“尚未接入数据库”，不把原型发布按钮冒充真实业务。助手功能本次不扩展。

这不是可公开上线的系统：暂无学校身份验证、注册/密码找回、登录限流、完整 AI 资料权限或正式 MySQL 迁移验证。Session 与 BCrypt/CSRF 是这条本地业务的安全起点，不代表所有模块安全已完成。

## 重跑验证

```powershell
mvn -DskipTests=false test
cd frontend
npm run build
npm run test:prototype
npm run test:business
cd ..
node scripts\check-recruitment.mjs
node scripts\smoke.mjs
```

`check-recruitment` 会用上述虚构账号生成/审核验收记录，不删除已有记录。停止并重新启动校园服务后，执行 `node scripts\check-recruitment.mjs restored`，检查之前保存的同一申请与成员关系，而不是新建替代记录。测试明细见 `RECRUITMENT-VERIFICATION.md`。
