# 单机服务器部署指南

这套配置面向一个 Linux 主机、一个校园后端、一个 AI 后端。它已经过本机隔离容器演练，不等于已经在公网或真实学校生产环境验收。现有 Windows 开发脚本与 H2 数据仍保留。

## 先准备什么

- 建议从 2核4GB、足够磁盘空间的 x86_64 Linux 主机起步；实际容量须在目标服务器验证。单机不是高可用，更新/备份有短时维护窗口。
- Docker Engine + Compose V2、Git、OpenSSL、Bash。构建机还要 Java 17/JDK、Maven、与前端兼容的 Node/npm；可在独立构建机打包后通过 `docker save/load` 传递三份应用镜像与对应 `deploy/releases/` 元数据，不必在小服务器上长期保留构建工具。
- 自己的域名和匹配域名的有效 TLS 证书。中国内地网站需完成相应备案；没有域名/证书时先做受限的本机或内网验证，不要直接公开开发端口。
- 不要购买 GPU 作为第一步。先使用管理员配置的兼容云 API；学校模型服务器可达后，再更改明确允许的模型地址。

官方依据：[Compose 就绪顺序](https://docs.docker.com/compose/how-tos/startup-order/)、[Nginx 反向代理](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_pass)、[Flyway MySQL](https://documentation.red-gate.com/fd/mysql-277579322.html)。

## 1. 本地构建与服务器配置

在项目根目录操作，先复制配置并编辑：

```bash
cp deploy/.env.example deploy/.env
bash deploy/init-secrets.sh
```

`deploy/.env` 只放非密钥配置。将 `AI_MODEL_ALLOWED_URLS` 改为你准备用的聊天/Embedding 服务的精确基础地址列表（逗号分隔），不能填任意地址通配符。聊天模型和 Embedding 分别在平台页保存、测试和确认启用；首次部署默认 LOCAL 摘录模式，不自动下载模型、不调用收费模型、不自动云端兜底。

`init-secrets.sh` 只在文件缺失时生成密钥，不覆盖现有内容，也不打印它们。`deploy/secrets/` 目录只允许拥有者访问；子文件对容器内的非 root 服务可读。Docker 管理权限等同高权限，不要把陌生用户加入 docker 组。

首次管理员默认 `campus_admin`，密码在服务器本地的 `deploy/secrets/admin_password`，不要发到聊天、截图或 Git。可在 `.env` 中修改 `ADMIN_USERNAME`。管理员仅在账号表完全为空时初始化；已有管理员不会被重置，已有普通账号不会被自动提权。保留密钥文件也不会在重启时重置密码。

特别保管 `ai_master_key`：它是模型 API Key 的加密主密钥，丢失后无法解密原有保存配置。数据库备份和主密钥要分别安全保管，建议各自做加密异地副本。

## 2. 配置 HTTPS 和访问范围

将有效证书和私钥分别放到：

```text
deploy/tls/fullchain.pem
deploy/tls/privkey.pem
```

私钥权限应为 600、目录 700。证书续期后执行 `docker compose --env-file deploy/.env -f deploy/compose.yml exec web nginx -s reload`；该命令不包含应用镜像更新，续期与自动续期监控由服务器运维负责。启动脚本拒绝有效期不足 24 小时的证书；域名匹配与公信链仍需浏览器/客户端实测。

默认 `HTTP_BIND=127.0.0.1`，不会公开网站。确认云安全组、主机防火墙、证书、管理员密码后，才改成 `HTTP_BIND=0.0.0.0` 对外开放。网站仅映射 80/443；SSH 另行限制为运维来源 IP。不要对公网开放 8090、8091、3306、6379、11434 或数据库管理页面。不要直接套用会锁死 SSH 的防火墙命令。

MySQL/Redis 没有宿主机端口映射；两个后端只有容器内地址。Nginx 覆盖浏览器传入的转发身份/协议头，后端只在这条受控路径上信任代理。不要另行给后端发布公网端口。

## 3. 构建、启动、停止

```bash
bash deploy/build-release.sh v2026-10-19
bash deploy/manage.sh start v2026-10-19
bash deploy/manage.sh status
bash deploy/manage.sh logs
```

构建脚本执行后端测试打包、前端专项/业务/原型测试及构建，然后构建三个带版本的应用镜像。已有版本标签不允许覆盖。底层镜像按 digest 固定，更新它们要重新测试，不能盲目追随 latest。首次拉取依赖和镜像需要可用的网络。

正式环境强制 MySQL + Flyway 版本化建表，Hibernate 只校验结构，不自动变更表；两个服务分别使用各自数据库账号，不能读取另一个数据库。`prod` 不加载 `demo`，同时开启两者会失败；已存在的公开演示管理账号密码也会被拒绝。全新库不会自动放入演示社团、资料或活动。

管理员不继承负责人权限。本次只做首次平台管理员初始化，没有新增负责人发放/任意角色管理页面；首次真实业务使用前仍需确定正式负责人开户流程。不要为方便而启用 demo 或直接公开本机测试账号。

停止只停止容器，不删除数据卷：

```bash
bash deploy/manage.sh stop
```

## 4. 更新和回滚

保留旧镜像、`deploy/.release`、`.previous` 和 `deploy/releases/`。拉取新代码后使用新版本标签：

```bash
bash deploy/build-release.sh v2026-10-20
bash deploy/manage.sh update v2026-10-20
bash deploy/manage.sh rollback
```

更新会先备份，再启动新镜像，等待就绪并检查 Nginx。成功后才更新版本记录；失败时当前容器可能已部分替换，必须查看 status/logs，不能把版本文件没变理解为仍完整运行旧版本。

回滚只回滚应用镜像，不回滚用户数据、密钥或 `.env`。只有两个版本的数据库迁移清单和部署拓扑摘要相同时才自动允许；有数据库/拓扑变化就拒绝，需人工评估兼容性并做恢复演练。不提供自动覆盖生产数据库的命令。单机方案没有承诺零停机更新。

## 5. 数据库备份与恢复演练

```bash
bash deploy/manage.sh backup
bash deploy/restore-trial.sh backups/具体时间目录
```

备份会暂停两个后端的写入，导出两库（含二进制、迁移记录），保存 SQL 校验和与规范化行数据摘要，然后只恢复原先运行的后端。MySQL 自身不停机。备份目录私有，不进入镜像/Git，不自动删除历史备份；应监控磁盘和配置异地加密副本。可用服务器的定时任务调用 `manage.sh backup`，但需避开业务时段并监控失败结果。

恢复演练始终创建一个全新、无网络的 MySQL 容器和独立数据卷，核对 SQL 校验和、导入后重新导出所有行并对比摘要。成功后停止测试容器，保留数据卷便于核查。失败也保留现场。它不会覆盖生产库；真实灾难恢复需要先确认目标、维护窗口和数据丢失范围，不能直接拿旧备份覆盖在线数据。

## 6. 原有 H2 数据怎么迁移

推荐正式上线新建干净数据库，旧虚构测试资料留在本地。如果确实需要保留数据，先在隔离 MySQL 演练，不自动导入线上：

1. 停止对应 H2 写入，制作关闭状态的备份；只使用备份文件，不能打开正在写入的数据库文件。
2. 新建两个空 MySQL 目标库，运行相同的 Flyway 迁移。**不能先启动会初始化管理员的生产应用**，否则账号表非空，导入器会拒绝。
3. 在 JDK 17 构建机生成 JDBC/Flyway 类路径，运行下面的工具；密码只从本地文件读取，不放在命令行参数中。

工具需要能访问隔离目标 MySQL：容器名 `mysql` 只在其 Docker 网络内解析，宿主机不能直接把它当公网地址使用。演练时可给**隔离迁移库**临时映射 `127.0.0.1:13316:3306`，工具连接本机 13316；完成后停止该测试容器。不要为了迁移把正式数据库映射到 `0.0.0.0`，也不要向在线非空库导入。

```bash
mvn -pl campus-service dependency:build-classpath -Dmdep.outputFile=/绝对路径/jdbc-classpath.txt
CP=$(cat /绝对路径/jdbc-classpath.txt)
java -cp "$CP" deploy/MigrateSchema.java \
  'jdbc:mysql://目标主机:3306/campus_business?serverTimezone=Asia/Shanghai' \
  运维数据库账号 /私有目录/数据库密码文件 campus-service/src/main/resources/db/migration
java -cp "$CP" deploy/H2Import.java \
  /关闭备份目录/campus \
  'jdbc:mysql://目标主机:3306/campus_business?serverTimezone=Asia/Shanghai' \
  运维数据库账号 /私有目录/数据库密码文件 --confirm-empty-target
```

AI 库对应 `campus_ai`、`ai-service/src/main/resources/db/migration` 与备份基名 `ai`。Windows 类路径分隔符与 Linux 不同，应使用当前构建环境生成的类路径。首次导入账号使用的运维权限与运行时数据库账号不同，不应为了迁移扩大公开服务的权限。

工具以只读方式打开 H2，要求目标业务表全部为空，按外键依赖排序，保留 ID、BLOB、中文和时间；每张表摘要一致后才提交。错误会回滚当前目标库，不自动去重、合并或修改源文件。两库分别提交，因此两库完成前不得启动线上服务。已有非空目标一律拒绝。

导入不会清除弱演示账号或改变权限，不能把演练库直接公开。源库中的模型配置密文还需要**源系统原来的 AI_CONFIG_MASTER_KEY**，不能用新生成主密钥替代；没有旧主密钥应明确重新配置密钥，不能伪装解密成功。

## 7. AI 额度和资源保护

- 每账号每分钟限制沿用原有 Redis 限流。
- 新增默认每账号每天 50 次、全平台每天 1000 次的 AI 业务准入额度，部署环境可调整。UTC 零点切换日期（北京时间 08:00），Redis AOF 保留计数，不能靠重启恢复额度。
- 被准入的上传、推荐、问答、策划等请求会计数，包括后续校验/模型失败；不退款，不等于精确 Token 或人民币账单。一个 Agent 请求可能包含多次模型调用，上传也可能有多批 Embedding。还应在模型供应商侧设置费用告警/上限。
- AI 服务所有聊天/Embedding 及管理员连接测试共用最多 2 个同步槽位，不无限排队；平台管理员测试不计入学生业务每日额度。适用于**单个 AI 实例**，不是跨多个实例的全局并发控制。
- Redis 额度检查失败拒绝新 AI 请求；校园查询和已授权历史不应因此中断。Redis 不自动淘汰额度键，内存不足也不能静默放行。
- 容器内存有上限、Java 限制堆占比、日志限制每容器 3×10MB。没有承诺在恶意 PDF、所有模型输入或所有访问量下具备完整资源隔离。

## 8. 上线前验收清单

- 有效域名/公信 HTTPS、外网端口扫描、证书续期和 SSH 权限。
- 初始管理员登录、密码修改、禁止 demo、负责人开户办法。
- 在实际 MySQL 上逐角色走通真实业务与权限；备份异地保存并恢复演练。
- 目标服务器内存/磁盘/并发容量、供应商预算、依赖安全扫描与升级计划。
- 真实学校身份、隐私与资料授权、备案及运营责任明确。

本次没有购买服务器、配置真实 DNS/备案、公网开放端口、测试收费模型或恢复覆盖任何真实生产库。也没有实现学校 SSO、用户充值、精确 Token 账单、集群高可用或完整安全审计。

具体本机证据见 [部署包验收记录](ACCEPTANCE.md)。
