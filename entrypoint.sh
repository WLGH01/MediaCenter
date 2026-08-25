#!/bin/sh
set -e

# 如果没有配置 PGDATA，使用默认位置
if [ -z "$PGDATA" ]; then
  export PGDATA="/var/lib/postgresql/data"
fi

# 确保 Unix socket 锁文件目录存在且 postgres 用户有写入权限
mkdir -p /run/postgresql
chown -R postgres:postgres /run/postgresql

# 初始化 PostgreSQL 数据库目录
if [ ! -s "$PGDATA/PG_VERSION" ]; then
  echo "[PostgreSQL] 初始化数据库目录于 $PGDATA..."
  mkdir -p "$PGDATA"
  # 修改权限，因为 postgres 守护进程必须由 postgres 用户运行
  chown -R postgres:postgres "$PGDATA"
  
  # 以 postgres 用户身份运行 initdb
  su-exec postgres initdb -D "$PGDATA"
  
  # 配置允许本地连接
  echo "host all all all md5" >> "$PGDATA/pg_hba.conf"
  echo "listen_addresses = '*'" >> "$PGDATA/postgresql.conf"
fi

# 启动 PostgreSQL 后台运行
echo "[PostgreSQL] 启动数据库服务..."
chown -R postgres:postgres "$PGDATA"
su-exec postgres pg_ctl -D "$PGDATA" -o "-c listen_addresses='*'" -w start

# 默认内置数据库连接配置
POSTGRES_USER=${POSTGRES_USER:-mediacenter}
POSTGRES_DB=${POSTGRES_DB:-mediacenter}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-change-me}

# 检查/创建默认的用户和数据库
echo "[PostgreSQL] 检查或创建默认数据库和用户..."
# 以 postgres 超级用户身份执行 SQL
su-exec postgres psql -U postgres -d postgres -c "SELECT 1 FROM pg_roles WHERE rolname='$POSTGRES_USER'" | grep -q 1 || \
  su-exec postgres psql -U postgres -d postgres -c "CREATE USER $POSTGRES_USER WITH PASSWORD '$POSTGRES_PASSWORD' SUPERUSER;"

su-exec postgres psql -U postgres -d postgres -c "SELECT 1 FROM pg_database WHERE datname='$POSTGRES_DB'" | grep -q 1 || \
  su-exec postgres psql -U postgres -d postgres -c "CREATE DATABASE $POSTGRES_DB OWNER $POSTGRES_USER;"

# 强制设置应用所需的 DATABASE_URL（连接本地内置的 PostgreSQL 数据库）
export DATABASE_URL="postgres://${POSTGRES_USER}:${POSTGRES_PASSWORD}@127.0.0.1:5432/${POSTGRES_DB}"
echo "[Config] 已配置数据库连接为: postgres://${POSTGRES_USER}:****@127.0.0.1:5432/${POSTGRES_DB}"

# 启动主应用 MediaCenter (以非 root 用户 mediacenter 运行)
echo "[MediaCenter] 启动媒体中心服务..."
# 确保上传目录有正确的权限
mkdir -p /app/uploads
chown -R mediacenter:mediacenter /app/uploads

# 转移到 mediacenter 用户执行应用
exec su-exec mediacenter node index.js
