package com.mohanlv.cicd.service

import com.mohanlv.cicd.entity.BuildRecord
import com.mohanlv.cicd.github.GithubService
import com.mohanlv.cicd.repository.AppRepository
import com.mohanlv.cicd.repository.BuildFlowRepository
import com.mohanlv.cicd.repository.BuildRecordRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class BuildService(
    private val appRepository: AppRepository,
    private val buildRecordRepository: BuildRecordRepository,
    private val buildFlowRepository: BuildFlowRepository,
    private val githubService: GithubService,
    private val gitHubAppService: com.mohanlv.cicd.github.GitHubAppService
) {
    @Transactional
    fun triggerBuild(appId: Long, flowId: Long?, buildParams: Map<String, String>?, workflowIdOverride: String?, branchOverride: String?): BuildRecord {
        val app = appRepository.findById(appId).orElseThrow { NoSuchElementException("App 不存在: $appId") }
        // 如果是完整 path，提取文件名；否则直接使用
        val workflowIdRaw = workflowIdOverride ?: app.workflowId ?: throw IllegalArgumentException("App 未配置 Workflow ID")
        val workflowId = if (workflowIdRaw.contains("/")) {
            workflowIdRaw.substringAfterLast("/").let { if (it.endsWith(".yml")) it else "$it.yml" }
        } else {
            workflowIdRaw
        }
        val installationId = app.installationId ?: gitHubAppService.getInstallationIdByRepo(app.repoUrl)
        val branch = branchOverride ?: app.branch

        val (owner, repo) = githubService.parseRepoInfo(app.repoUrl)

        // 获取构建序号
        val buildNumber = (buildRecordRepository.countByAppId(appId) + 1).toInt()

        // 获取构建流程配置（可选）
        val flowConfig = if (flowId != null) {
            buildFlowRepository.findById(flowId).orElseThrow { NoSuchElementException("构建流程不存在: $flowId") }
        } else {
            buildFlowRepository.findByAppIdAndIsDefaultTrue(appId).orElse(null)
        }

        // 创建构建记录
        val record = BuildRecord(
            app = app,
            buildNumber = buildNumber,
            status = "pending"
        )
        val savedRecord = buildRecordRepository.save(record)

        // 触发 GitHub Actions（不传 inputs，只触发 workflow）
        val runResult = githubService.triggerWorkflow(installationId, owner, repo, workflowId, branch, emptyMap())

        // 更新记录
        savedRecord.workflowRunId = (runResult["runId"] as Long).toString()
        savedRecord.startedAt = LocalDateTime.now()
        buildRecordRepository.save(savedRecord)

        return savedRecord
    }

    fun getBuildRecords(appId: Long, pageable: Pageable): Page<BuildRecord> {
        return buildRecordRepository.findByAppIdOrderByCreatedAtDesc(appId, pageable)
    }

    fun getBuildRecord(id: Long): BuildRecord? {
        return buildRecordRepository.findById(id).orElse(null)
    }

    @Transactional
    fun updateBuildStatus(recordId: Long, status: String, artifactUrl: String? = null, logs: String? = null) {
        val record = buildRecordRepository.findById(recordId).orElse(null ?: return)
        record.status = status
        if (status == "success" || status == "failed") {
            record.finishedAt = LocalDateTime.now()
        }
        artifactUrl?.let { record.artifactUrl = it }
        logs?.let { record.logs = it }
        buildRecordRepository.save(record)
    }

    @Scheduled(fixedDelay = 30000)
    @Transactional
    fun syncBuildStatus() {
        val runningRecords = buildRecordRepository.findAll()
            .filter { it.status == "pending" || it.status == "running" }

        for (record in runningRecords) {
            val app = record.app ?: continue
            val runId = record.workflowRunId ?: continue
            val installationId = app.installationId ?: continue

            try {
                val (owner, repo) = githubService.parseRepoInfo(app.repoUrl)
                val ghRun = githubService.getWorkflowRun(installationId, owner, repo, runId.toLong())

                if (ghRun == null) {
                    continue
                }

                val ghStatus = ghRun["status"] as String
                val ghConclusion = ghRun["conclusion"] as String

                val newStatus = when (ghStatus) {
                    "in_progress", "queued" -> "running"
                    "completed" -> if (ghConclusion == "success") "success" else "failed"
                    else -> record.status
                }

                if (newStatus != record.status) {
                    record.status = newStatus
                    if (newStatus == "success" || newStatus == "failed") {
                        record.finishedAt = LocalDateTime.now()
                    }
                }

                if (newStatus == "success" && record.artifactUrl == null) {
                    val artifactUrl = githubService.downloadArtifact(installationId, owner, repo, runId.toLong(), "app-release")
                    record.artifactUrl = artifactUrl
                }

                buildRecordRepository.save(record)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}