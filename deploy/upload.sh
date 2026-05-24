#!/usr/bin/env bash
# 将本地打好的 jar 上传到云服务器并重启
# 用法: bash deploy/upload.sh
# 依赖: 本机已安装 expect
set -euo pipefail

SERVER_HOST="47.99.202.93"
SERVER_USER="root"
SERVER_PORT="22"
SERVER_PASS="${HIGHWAY_SSH_PASS:-Chen@123150}"
REMOTE_DIR="/opt/highway-agent"
JAR_NAME="highway-agent-0.0.1-SNAPSHOT.jar"
LOCAL_JAR="target/${JAR_NAME}"
REMOTE_JAR="${REMOTE_DIR}/${JAR_NAME}"

if ! command -v expect >/dev/null 2>&1; then
  echo "[ERROR] 本机未安装 expect。macOS 自带；Linux 请先 apt install expect 或 yum install expect。"
  exit 1
fi

if [ ! -f "${LOCAL_JAR}" ]; then
  echo "[ERROR] 找不到 ${LOCAL_JAR}，请先在 IDEA 里执行 Maven package（或 mvn clean package -DskipTests）"
  exit 1
fi

run_ssh() {
  local cmd="$1"
  expect <<EOF
set timeout 60
log_user 1
spawn ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -p ${SERVER_PORT} ${SERVER_USER}@${SERVER_HOST} "${cmd}"
expect {
  -re "(?i)password:" { send "${SERVER_PASS}\r"; exp_continue }
  eof
}
EOF
}

run_scp() {
  local src="$1"
  local dst="$2"
  expect <<EOF
set timeout 600
log_user 1
spawn scp -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -P ${SERVER_PORT} ${src} ${SERVER_USER}@${SERVER_HOST}:${dst}
expect {
  -re "(?i)password:" { send "${SERVER_PASS}\r"; exp_continue }
  eof
}
EOF
}

echo "[INFO] 确保远端目录存在"
run_ssh "mkdir -p ${REMOTE_DIR}/logs"

echo "[INFO] 上传启动脚本"
run_scp "deploy/server-run.sh" "${REMOTE_DIR}/server-run.sh"
run_ssh "chmod +x ${REMOTE_DIR}/server-run.sh"

echo "[INFO] 上传 jar"
run_scp "${LOCAL_JAR}" "${REMOTE_JAR}"

echo "[INFO] 重启服务"
run_ssh "bash ${REMOTE_DIR}/server-run.sh restart"

echo "[INFO] 当前状态"
run_ssh "bash ${REMOTE_DIR}/server-run.sh status"

echo "[OK] 部署完成。访问: http://${SERVER_HOST}:9999/"
echo "[INFO] 查看日志: ssh ${SERVER_USER}@${SERVER_HOST} 'bash ${REMOTE_DIR}/server-run.sh tail'"
