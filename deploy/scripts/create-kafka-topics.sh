#!/usr/bin/env bash
# 创建 GISO 生产 Kafka Topic（在已有 Kafka 集群上执行）
# 用法：KAFKA_BOOTSTRAP=broker1:9092 ./deploy/scripts/create-kafka-topics.sh
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"
RF="${KAFKA_REPLICATION_FACTOR:-3}"
PARTITIONS_RAW="${KAFKA_PARTITIONS_RAW:-8}"
PARTITIONS_TEST="${KAFKA_PARTITIONS_TEST:-4}"
PARTITIONS_QUAR="${KAFKA_PARTITIONS_QUARANTINE:-2}"

kafka_cmd() {
  if command -v kafka-topics.sh &>/dev/null; then
    kafka-topics.sh --bootstrap-server "$BOOTSTRAP" "$@"
  elif command -v kafka-topics &>/dev/null; then
    kafka-topics --bootstrap-server "$BOOTSTRAP" "$@"
  else
    docker run --rm apache/kafka:3.9.0 \
      /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" "$@"
  fi
}

create_topic() {
  local name="$1" parts="$2"
  echo "[kafka] create $name partitions=$parts rf=$RF"
  kafka_cmd --create --if-not-exists \
    --topic "$name" \
    --partitions "$parts" \
    --replication-factor "$RF" \
    --config retention.ms=604800000
}

create_topic events_raw "$PARTITIONS_RAW"
create_topic events_raw_test "$PARTITIONS_TEST"
create_topic events_quarantine "$PARTITIONS_QUAR"

echo "[kafka] topics:"
kafka_cmd --list
