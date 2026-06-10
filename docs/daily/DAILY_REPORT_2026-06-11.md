# 工作日报 - 2026-06-11

## 完成内容

### 1. 构建记录唯一标识改造
- 后端新增 `/api/builds/run/{workflowRunId}`端点，支持通过 workflowRunId 查询构建记录
- 后端新增 `/api/builds/run/{workflowRunId}/logs` 端点，支持通过 workflowRunId 获取日志
- 前端路由改为 `/app/:id/builds/:workflowRunId`，使用 workflowRunId 作为路由参数

### 2. APK 文件名包含 Run ID
- 修改 workflow 模板，APK 文件名改为 `{原名}-{run_id}.apk`（如 `app-release-27294774299.apk`）
- 使用 `github.run_id` 而非 `github.run_number`

### 3. 构建记录列表优化
- 第一列改为显示可点击的 workflowRunId，跳转到 GitHub Actions 页面
- Commit 列改为可点击跳转到 GitHub commit 页面
- 状态列 running 使用 SVG 动画图标

### 4. 构建详情页
- 新建 `BuildRecordDetail.tsx` 页面
- 展示构建编号、状态、Commit、时长、时间、Actions 链接、下载链接和二维码
- 构建日志支持刷新和下载

### 5. 样式优化
- 按钮样式：渐变背景、阴影、hover 上浮效果、点击缩放动画
- 新增站点图标（闪电符号 SVG）
- 状态图标与文案上下居中

### 6. 日志获取优化
- 构建过程中（queued/in_progress 或404）返回友好提示"日志正在生成中，请稍后刷新"
- 使用 `instanceFollowRedirects = true` 自动跟随重定向

### 7. 基础设施
- 重启脚本改为使用 `fly deploy`
- 修复多个 TypeScript 类型错误

## 提交记录
- `b46108d` feat: 使用 workflowRunId 作为构建记录唯一标识
- `12a8591` feat: 修复 CORS 问题并优化构建记录页面

## 待优化
- workflow 更新会创建 commit，可考虑 squash merge 减少噪音
