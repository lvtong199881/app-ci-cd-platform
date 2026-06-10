# App CI/CD 平台

## 项目结构
- `backend/` - Spring Boot 后端 (Kotlin)
- `frontend/` - React 前端

## 重启服务

项目配置了 `/skills restart` skill，支持以下命令：
- `重启` - 重启后端和前端
- `重启后端` - 只重启后端 (8080)
- `重启前端` - 只重启前端 (3000)

也可以直接用脚本：
- `./restart-backend.sh` - 重启后端
- `./restart-frontend.sh` - 重启前端

## 数据库
- 默认使用 H2 内存数据库 (数据重启后丢失)
- `application-local.yml` 使用 MySQL
- 激活 MySQL：直接修改 `application.yml` 的 datasource 配置

## API 端口
- 后端：8080
- 前端：3000
