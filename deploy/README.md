# Highway Agent 部署说明

目标服务器: `root@47.99.202.93:22`
部署目录: `/opt/highway-agent`
应用端口: `9999`
日志文件: `/opt/highway-agent/logs/app.log`

## 1. 本地打包

在 IDEA 中执行 Maven 生命周期：

- `clean`
- `package`（可勾选 Skip Tests）

产物路径：`target/highway-agent-0.0.1-SNAPSHOT.jar`

或命令行（需要本机有 mvn）：

```bash
mvn clean package -DskipTests
```

## 2. 上传到服务器

```bash
bash deploy/upload.sh
```

脚本会通过 `expect` 自动输入 SSH 密码（默认 `Chen@123150`，可用环境变量 `HIGHWAY_SSH_PASS` 覆盖）并完成：

- 在服务器上创建 `/opt/highway-agent/logs`
- 上传 jar 到 `/opt/highway-agent/`
- 上传 `server-run.sh` 启动脚本并赋可执行权限
- 自动 `restart` + `status`

## 3. 启动 / 重启 / 查看日志

```bash
ssh root@47.99.202.93
bash /opt/highway-agent/server-run.sh restart   # 首次或更新后用 restart
bash /opt/highway-agent/server-run.sh status
bash /opt/highway-agent/server-run.sh tail      # 实时查看日志
bash /opt/highway-agent/server-run.sh stop
```

## 4. 访问

- 浏览器: `http://47.99.202.93:9999/`
- 确保云服务器安全组放行 `9999/tcp`

## 5. 注意

- 当前 `application.yml` 里的 MySQL / MinIO / DashScope / Tavily 凭证会直接随 jar 上传，仅适合你自用环境。
- 升级时再次执行 `deploy/upload.sh` 然后 `restart` 即可。
