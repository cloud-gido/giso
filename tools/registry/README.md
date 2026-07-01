# GISO 注册表 PostgreSQL（共用 RDS）

生产注册表持久化在 **PostgreSQL 库 `giso`**（与 GIDO / DataEase 同 RDS 实例）。

## Doppler（project: gamelinelab, config: dev）

| Doppler Key | K8s Secret | 说明 |
|-------------|------------|------|
| `INFRA_GISO_DB_HOST` | `GISO_DB_HOST` | 与 `INFRA_DATAEASE_DB_HOST` 相同 |
| `INFRA_GISO_DB_PORT` | `GISO_DB_PORT` | 通常 `5432` |
| `INFRA_GISO_DB_NAME` | `GISO_DB_NAME` | `giso` |
| `INFRA_GISO_DB_USER` | `GISO_DB_USER` | `giso_app` |
| `INFRA_GISO_DB_PASSWORD` | `GISO_DB_PASSWORD` | 独立强密码 |

## 一次性建库（RDS 主账号）

```bash
export RDS_MASTER_HOST=dev-db.proxy.sa-east-1.rds.amazonaws.com
export RDS_MASTER_USER=postgres
export RDS_MASTER_PASSWORD='***'
export GISO_DB_PASSWORD='***'
bash tools/registry/setup.sh --create-db --migrate --import
```

或 Gateway 首次启动：库已存在且为空时，自动从镜像内 `/app/schema` 种子导入。

## 运维命令

```bash
# 从 DB 导出 YAML（与 Git diff）
python3 tools/registry/export_yaml.py --check

# 手动全量导入
python3 tools/registry/import_yaml.py
```

## K8s

- Gateway：`registry.backend=postgres`，readiness `/ready`
- CronJob `giso-registry-export`：每小时导出快照到 Pod emptyDir（可扩展 Git PR）
