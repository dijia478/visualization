#!/bin/bash
set -eu

BASE_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
PACKAGE_NAME=visualization-0.0.1-SNAPSHOT.jar
PID_FILE="${BASE_DIR}/logs/visualization.pid"
START_LOG="${BASE_DIR}/logs/startup.log"

if ! command -v java >/dev/null 2>&1; then
    echo "Java is required. Install JRE/JDK 8 before starting."
    exit 1
fi
if [ ! -f "${BASE_DIR}/lib/${PACKAGE_NAME}" ]; then
    echo "Application package not found. Run start.sh from the extracted release directory."
    exit 1
fi

mkdir -p "${BASE_DIR}/logs"
if [ -f "${PID_FILE}" ]; then
    pid=$(cat "${PID_FILE}")
    command_line=$(ps -ww -p "${pid}" -o args= 2>/dev/null || true)
    case "${command_line}" in
        *"${BASE_DIR}/lib/${PACKAGE_NAME}"*)
            echo "Application is already running with PID ${pid}."
            exit 0
            ;;
    esac
    rm -f "${PID_FILE}"
fi

# Resolve logging and file-backed data paths relative to this release.
cd "${BASE_DIR}"
nohup java -Xms512m -Xmx512m -XX:+UseG1GC \
    -Djava.awt.headless=true -Dfile.encoding=UTF-8 \
    "-Dloader.path=${BASE_DIR}/config,${BASE_DIR}/lib" \
    -jar "${BASE_DIR}/lib/${PACKAGE_NAME}" \
    "--spring.config.location=file:${BASE_DIR}/config/application.yml" \
    >"${START_LOG}" 2>&1 < /dev/null &
pid=$!
echo "${pid}" > "${PID_FILE}"

sleep 5
if kill -0 "${pid}" 2>/dev/null; then
    echo "Application started with PID ${pid}."
    echo "Calculator: http://localhost:8080/calculator/index.html (default configuration)"
    echo "Startup log: ${START_LOG}"
else
    rm -f "${PID_FILE}"
    echo "Application failed to start. See ${START_LOG}."
    tail -n 30 "${START_LOG}"
    exit 1
fi
