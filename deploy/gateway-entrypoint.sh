#!/bin/sh
# 数据卷挂载会覆盖镜像内 /data，启动前确保子目录存在且 giso 可写。
set -e
mkdir -p /data/events /data/spill /data/screenshots
chown -R giso:giso /data
exec su-exec giso java -jar /app/gateway.jar "$@"
