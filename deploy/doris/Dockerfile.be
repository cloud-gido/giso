# Mac / Docker Desktop：跳过 swap / nofile / max_map_count 启动检查
FROM apache/doris:be-ubuntu-2.1.7
USER root
# 启动脚本前 150 行里的 exit 1 多为环境检查，本地 demo 全部注释掉
RUN awk 'NR<=150 && /exit 1/ && $0 !~ /^[[:space:]]*#/ { sub(/exit 1/, "# exit 1") }1' \
      /opt/apache-doris/be/bin/start_be.sh > /tmp/start_be.sh \
    && mv /tmp/start_be.sh /opt/apache-doris/be/bin/start_be.sh \
    && chmod +x /opt/apache-doris/be/bin/start_be.sh
ENV SKIP_CHECK_ULIMIT=true
