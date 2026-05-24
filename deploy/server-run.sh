#!/usr/bin/env bash
# 在云服务器上启动/停止/重启 highway-agent
# 用法: bash server-run.sh {start|stop|restart|status|tail}
set -euo pipefail

APP_DIR="/opt/highway-agent"
JAR_NAME="highway-agent-0.0.1-SNAPSHOT.jar"
JAR_PATH="${APP_DIR}/${JAR_NAME}"
LOG_DIR="${APP_DIR}/logs"
LOG_FILE="${LOG_DIR}/app.log"
PID_FILE="${APP_DIR}/app.pid"
JAVA_OPTS="-Xms512m -Xmx1024m -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"
APP_PORT="9999"

mkdir -p "${LOG_DIR}"

start() {
  if [ -f "${PID_FILE}" ] && kill -0 "$(cat ${PID_FILE})" 2>/dev/null; then
    echo "[WARN] 已在运行，PID=$(cat ${PID_FILE})"
    exit 0
  fi
  if [ ! -f "${JAR_PATH}" ]; then
    echo "[ERROR] 未找到 ${JAR_PATH}"
    exit 1
  fi
  echo "[INFO] 启动 highway-agent，端口 ${APP_PORT}"
  nohup java ${JAVA_OPTS} -jar "${JAR_PATH}" --server.port=${APP_PORT} >> "${LOG_FILE}" 2>&1 &
  echo $! > "${PID_FILE}"
  sleep 2
  echo "[OK] 已启动，PID=$(cat ${PID_FILE})，日志: ${LOG_FILE}"
}

stop() {
  if [ ! -f "${PID_FILE}" ]; then
    echo "[INFO] 未发现 PID 文件，可能未运行"
    return 0
  fi
  PID=$(cat "${PID_FILE}")
  if kill -0 "${PID}" 2>/dev/null; then
    echo "[INFO] 停止 PID=${PID}"
    kill "${PID}"
    for i in $(seq 1 20); do
      if kill -0 "${PID}" 2>/dev/null; then sleep 1; else break; fi
    done
    if kill -0 "${PID}" 2>/dev/null; then
      echo "[WARN] 优雅停止失败，强杀"
      kill -9 "${PID}" || true
    fi
  fi
  rm -f "${PID_FILE}"
  echo "[OK] 已停止"
}

status() {
  if [ -f "${PID_FILE}" ] && kill -0 "$(cat ${PID_FILE})" 2>/dev/null; then
    echo "[RUNNING] PID=$(cat ${PID_FILE})"
  else
    echo "[STOPPED]"
  fi
}

case "${1:-}" in
  start) start ;;
  stop) stop ;;
  restart) stop; start ;;
  status) status ;;
  tail) tail -n 200 -f "${LOG_FILE}" ;;
  *) echo "用法: $0 {start|stop|restart|status|tail}"; exit 1 ;;
esac
