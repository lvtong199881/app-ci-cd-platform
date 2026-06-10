# Fly.io 部署文档

## 部署概况

- **平台**: Fly.io
- **App名称**: cicd-673
- **公共 IP**: 137.66.33.70
- **访问地址**: http://137.66.33.70
- **数据库**: Neon PostgreSQL

## 项目配置

### 1. fly.toml

```toml
app = "cicd-673"

[build]
  dockerfile = "Dockerfile"

[env]
  PORT = "8080"
  SPRING_PROFILES_ACTIVE = "prod"

[org]
  name = "personal"

[vm]
  size = "performance-1x"
  memory = "2048mb"

[[services]]
  protocol = "tcp"
  internal_port = 8080

  [[services.ports]]
    port = 443

  [[services.ports]]
    port = 80
```

### 2. application-prod.yml

```yaml
spring:
  application:
    name: app-ci-cd-platform

  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}?sslmode=require
    username: ${DB_USER}
    password: ${DB_PASSWORD}

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        format_sql: true

  jackson:
    serialization:
      write-dates-as-timestamps: false
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: Asia/Shanghai

server:
  port: 8080

  cors:
    allowed-origins: "*"
    allowed-methods: GET,POST,PUT,DELETE,OPTIONS

github:
  app:
    id: ${GITHUB_APP_ID}
    private-key: ${GITHUB_APP_PRIVATE_KEY}

file:
  storage:
    path: /app/storage/apk
```

### 3. build.gradle.kts (数据库驱动变更)

```kotlin
// PostgreSQL (Neon)
runtimeOnly("org.postgresql:postgresql")
```

## 环境变量配置

### Fly.io Secrets

```bash
# GitHub App 配置
fly secrets set GITHUB_APP_ID=3909627
fly secrets set "GITHUB_APP_PRIVATE_KEY=$(cat private-key.pem)"

# 数据库配置
fly secrets set DB_HOST="ep-solitary-violet-aoon9dc2.c-2.ap-southeast-1.aws.neon.tech"
fly secrets set DB_PORT="5432"
fly secrets set DB_NAME="neondb"
fly secrets set DB_USER="neondb_owner"
fly secrets set DB_PASSWORD="your-password"
```

### Neon 数据库连接

- Host: `ep-solitary-violet-aoon9dc2.c-2.ap-southeast-1.aws.neon.tech`
- Port: 5432
- Database: neondb
- User: neondb_owner
- SSL: require

## 部署命令

```bash
cd backend

# 本地构建
./gradlew clean bootJar

# 部署
fly deploy

# 查看状态
fly status

# 查看日志
fly logs --machine <machine-id>

# 重启机器
fly machine stop <machine-id>
fly machine start <machine-id>

# 分配 IPv4
fly ips allocate-v4 -y
```

## 遇到的问题及解决方案

### 1. release_command 超时

**问题**: 部署时 `release_command = "./gradlew bootJar"` 超时

**解决**: 移除 release_command，改为本地构建后再部署

### 2. 内存不足 (OOM)

**问题**: 默认 VM 内存 256MB 导致 Java 进程被 kill

**解决**: 在 fly.toml 中设置 `memory = "2048mb"`，必须是 1024 的倍数

### 3. DATABASE_URL 格式问题

**问题**: `Driver org.postgresql.Driver claims to not accept jdbcUrl`

**原因**: 密码中包含 `@` 符号导致 URL解析错误

**解决**: 将完整的 DATABASE_URL 拆分为单独的参数：
- DB_HOST
- DB_PORT
- DB_NAME
- DB_USER
- DB_PASSWORD

### 4. application.yml 中的 MySQL 配置覆盖

**问题**: 主配置文件中的 MySQL 驱动配置覆盖了 prod profile

**解决**: 清理 application.yml，只保留共享配置，数据库配置放到 application-prod.yml

### 5. Fly.io MySQL Beta 限制

**问题**: Fly.io MySQL 处于私人 Beta，暂不开放

**解决**: 改用 Neon PostgreSQL

### 6. Secrets 未同步到所有机器

**问题**: "This secret is only deployed to some machines"

**解决**: 在 Fly.io Dashboard 点击 "Deploy Secrets" 按钮同步

## 访问方式

### 直接 IP访问
http://137.66.33.70

### 绑定自定义域名

1. 在 Fly.io Dashboard 添加域名：https://fly.io/apps/cicd-673/domains
2. 配置 DNS 记录指向 `137.66.33.70`
3. 选择免费域名可用 Freenom (freenom.com)

## 监控

- Dashboard: https://fly.io/apps/cicd-673/monitoring
- 日志: `fly logs --machine <machine-id>`

## 相关文件

- `backend/fly.toml` - Fly.io 部署配置
- `backend/src/main/resources/application-prod.yml` - 生产环境配置
- `backend/src/main/resources/application.yml` - 共享配置
- `backend/build.gradle.kts` - 构建配置（含 PostgreSQL 驱动）