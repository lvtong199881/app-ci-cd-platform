# App CI/CD 平台

支持多 App 管理、构建 APK、构建记录查看、自定义构建流程的 CI/CD 平台。

## 技术栈

- **后端**: Spring Boot 3.4 + Kotlin + JPA + MySQL
- **前端**: React 18 + TypeScript + Vite + React Router
- **构建执行**: GitHub Actions
- **产物存储**: 文件系统

## 快速开始

### 1. 启动后端

```bash
cd backend

# 创建 MySQL 数据库
mysql -u root -p -e "CREATE DATABASE cicd"

# 启动应用
./gradlew bootRun
```

或使用 IDE 打开运行 `CicdApplication.kt`

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
```

### 3. 访问

- 前端: http://localhost:3000
- 后端 API: http://localhost:8080/api

## 配置说明

### GitHub Settings

在 GitHub 仓库的 `.github/workflows/` 目录下创建 `app-build.yml`：

```yaml
name: App Build

on:
  workflow_dispatch:
    inputs:
      app_key:
        description: 'App Key'
        required: true
      build_record_id:
        description: 'Build Record ID'
        required: true
      flow_config:
        description: 'Flow Config JSON'
        required: true
      build_params:
        description: 'Build Parameters'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup JDK
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Setup Gradle
        uses: gradle/gradle-build-action@v2

      - name: Get Gradle wrapper
        run: gradle wrapper

      - name: Build APK
        env:
          FLOW_CONFIG: ${{ github.event.inputs.flow_config }}
        run: |
          # 解析 flow_config 并执行构建
          ./gradlew assembleRelease

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: app-release
          path: app/build/outputs/apk/release/*.apk
```

### application.yml 配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/cicd
    username: root
    password: your_password

github:
  token: ${GITHUB_TOKEN}  # GitHub Personal Access Token
```

## API 接口

### App 管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/apps | 列表 |
| POST | /api/apps | 创建 |
| GET | /api/apps/{id} | 详情 |
| PUT | /api/apps/{id} | 更新 |
| DELETE | /api/apps/{id} | 删除 |

### 构建

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/builds/trigger/{appId} | 触发构建 |
| GET | /api/builds/app/{appId} | 构建记录列表 |
| GET | /api/builds/{id} | 构建详情 |
| GET | /api/builds/{id}/logs | 构建日志 |

### 构建流程

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/flows/app/{appId} | 流程列表 |
| POST | /api/flows | 创建 |
| PUT | /api/flows/{id} | 更新 |
| DELETE | /api/flows/{id} | 删除 |

## 项目结构

```
app-ci-cd-platform/
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/com/mohanlv/cicd/
│   │   ├── CicdApplication.kt  # 入口
│   │   ├── controller/         # API 控制器
│   │   ├── service/            # 业务逻辑
│   │   ├── repository/         # 数据访问
│   │   ├── entity/             # 实体类
│   │   ├── github/             # GitHub API 交互
│   │   └── config/             # 配置类
│   └── src/main/resources/
│       └── application.yml
├── frontend/                   # H5 前端
│   ├── src/
│   │   ├── api/                # API 调用
│   │   ├── pages/              # 页面组件
│   │   └── App.tsx             # 路由入口
│   ├── index.html
│   └── vite.config.ts
└── README.md
```