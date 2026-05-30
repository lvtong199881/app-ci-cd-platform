package com.mohanlv.cicd.service

import com.mohanlv.cicd.entity.AppInfo
import com.mohanlv.cicd.entity.BuildFlow
import com.mohanlv.cicd.entity.BuildRecord
import com.mohanlv.cicd.github.GithubService
import com.mohanlv.cicd.repository.AppRepository
import com.mohanlv.cicd.repository.BuildFlowRepository
import com.mohanlv.cicd.repository.BuildRecordRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AppService(
    private val appRepository: AppRepository,
    private val buildFlowRepository: BuildFlowRepository,
    private val objectMapper: ObjectMapper
) {
    fun listApps(): List<AppInfo> = appRepository.findAll()

    fun getApp(id: Long): AppInfo? = appRepository.findById(id).orElse(null)

    fun getAppByKey(appKey: String): AppInfo? = appRepository.findByAppKey(appKey).orElse(null)

    @Transactional
    fun createApp(request: CreateAppRequest): AppInfo {
        val (autoAppName, appKey) = CreateAppRequest.parseRepoUrl(request.repoUrl)
        if (appRepository.existsByAppKey(appKey)) {
            throw IllegalArgumentException("App 已存在: $appKey")
        }
        val app = AppInfo(
            appName = request.appName?.takeIf { it.isNotBlank() } ?: autoAppName,
            appKey = appKey,
            repoUrl = request.repoUrl,
            branch = request.branch,
            buildConfig = request.buildConfig?.let { objectMapper.writeValueAsString(it) }
        )
        val savedApp = appRepository.save(app)

        return savedApp
    }

    @Transactional
    fun updateApp(id: Long, request: UpdateAppRequest): AppInfo {
        val app = appRepository.findById(id).orElseThrow { NoSuchElementException("App 不存在: $id") }
        request.appName?.let { app.appName = it }
        request.repoUrl?.let { app.repoUrl = it }
        request.branch?.let { app.branch = it }
        request.buildConfig?.let { app.buildConfig = objectMapper.writeValueAsString(it) }
        request.workflowId?.let { app.workflowId = it }
        app.updatedAt = java.time.LocalDateTime.now()
        return appRepository.save(app)
    }

    @Transactional
    fun deleteApp(id: Long) {
        appRepository.deleteById(id)
    }
}

data class CreateAppRequest(
    val repoUrl: String,
    val branch: String = "main",
    val buildConfig: Map<String, Any>? = null,
    val appName: String? = null
) {
    // 从 repoUrl 自动提取 appName 和 appKey
    companion object {
        fun parseRepoUrl(repoUrl: String): Pair<String, String> {
            val regex = Regex("github\\.com[/:]([^/]+)/([^/]+?)(?:\\.git)?$")
            val match = regex.find(repoUrl) ?: throw IllegalArgumentException("无效的 GitHub 仓库地址")
            val repoName = match.groupValues[2]
            // appKey: 仓库名 + 随机短码
            val appKey = repoName.lowercase() + "-" + java.util.UUID.randomUUID().toString().take(8)
            return Pair(repoName, appKey)
        }
    }
}

data class UpdateAppRequest(
    val appName: String? = null,
    val repoUrl: String? = null,
    val branch: String? = null,
    val buildConfig: Map<String, Any>? = null,
    val workflowId: String? = null
)