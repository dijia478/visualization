#!/bin/bash
set -eu

BASE_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
PACKAGE_NAME=visualization-0.0.1-SNAPSHOT.jar
PID_FILE="${BASE_DIR}/logs/visualization.pid"

if [ ! -f "${PID_FILE}" ]; then
    echo "No running application recorded for this release."
    exit 0
fi

pid=$(cat "${PID_FILE}")
command_line=$(ps -ww -p "${pid}" -o args= 2>/dev/null || true)
case "${command_line}" in
    *"${BASE_DIR}/lib/${PACKAGE_NAME}"*) ;;
    *)
        rm -f "${PID_FILE}"
        echo "Application is already stopped."
        exit 0
        ;;
esac

# SIGTERM lets Spring close the application; only target this release's PID.
kill -TERM "${pid}"
for attempt in {1..30}; do
    if ! kill -0 "${pid}" 2>/dev/null; then
        rm -f "${PID_FILE}"
        echo "Application stopped."
        exit 0
    fi
    sleep 1
done

echo "Application has not stopped after 30 seconds; PID ${pid} is still running."
echo "Check ${BASE_DIR}/logs/startup.log before retrying."
exit 1
