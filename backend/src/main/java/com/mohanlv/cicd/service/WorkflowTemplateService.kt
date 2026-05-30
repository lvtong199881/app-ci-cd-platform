package com.mohanlv.cicd.service

import com.mohanlv.cicd.entity.AppInfo
import com.mohanlv.cicd.github.GitHubAppService
import com.mohanlv.cicd.github.GithubService
import com.mohanlv.cicd.repository.AppRepository
import com.mohanlv.cicd.repository.BuildFlowRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Base64

@Service
class WorkflowTemplateService(
    private val appRepository: AppRepository,
    private val buildFlowRepository: BuildFlowRepository,
    private val githubService: GithubService,
    private val gitHubAppService: GitHubAppService,
    private val objectMapper: ObjectMapper
) {
    fun generateWorkflowYaml(app: AppInfo, flowId: Long?): String {
        val flowConfig = if (flowId != null) {
            buildFlowRepository.findById(flowId).orElse(null)?.flowConfig
        } else {
            buildFlowRepository.findByAppIdAndIsDefaultTrue(app.id).orElse(null)?.flowConfig
        } ?: """[{"type":"gradle","task":"assembleRelease","name":"构建 APK"}]"""

        val steps = objectMapper.readTree(flowConfig)

        val stepLines = buildString {
            steps.forEach { step ->
                val name = step.get("name").asText()
                val type = step.get("type").asText()
                val config = step.get("config") ?: objectMapper.createObjectNode()

                if (type == "gradle") {
                    val task = config.get("task")?.asText() ?: "assembleRelease"
                    appendLine("        - name: $name")
                    appendLine("          run: ./gradlew $task")
                    appendLine("          shell: bash")
                } else if (type == "shell") {
                    val script = config.get("script")?.asText() ?: ""
                    appendLine("        - name: $name")
                    appendLine("          run: $script")
                    appendLine("          shell: bash")
                }
            }
        }

        return buildString {
            appendLine("name: App Build")
            appendLine()
            appendLine("on:")
            appendLine("  workflow_dispatch:")
            appendLine("    inputs:")
            appendLine("      app_key:")
            appendLine("        description: 'App Key'")
            appendLine("        required: true")
            appendLine("      build_record_id:")
            appendLine("        description: 'Build Record ID'")
            appendLine("        required: true")
            appendLine("      flow_config:")
            appendLine("        description: 'Flow Config JSON'")
            appendLine("        required: true")
            appendLine("      build_params:")
            appendLine("        description: 'Build Parameters'")
            appendLine("")
            appendLine("jobs:")
            appendLine("  build:")
            appendLine("    runs-on: ubuntu-latest")
            appendLine("    steps:")
            appendLine("        - uses: actions/checkout@v4")
            appendLine()
            appendLine("        - name: Setup JDK")
            appendLine("          uses: actions/setup-java@v4")
            appendLine("          with:")
            appendLine("            distribution: 'temurin'")
            appendLine("            java-version: '17'")
            appendLine()
            appendLine("        - name: Setup Gradle")
            appendLine("          uses: gradle/gradle-build-action@v2")
            appendLine()
            appendLine("        - name: Get Gradle wrapper")
            appendLine("          run: gradle wrapper")
            appendLine()
            append(stepLines)
            appendLine()
            appendLine("        - name: Upload APK")
            appendLine("          uses: actions/upload-artifact@v4")
            appendLine("          with:")
            appendLine("            name: app-release")
            appendLine("            path: app/build/outputs/apk/release/*.apk")
        }
    }

    @Transactional
    fun createOrUpdateWorkflow(
        appId: Long,
        workflowName: String,
        flowId: Long?
    ): String {
        val app = appRepository.findById(appId).orElseThrow { NoSuchElementException("App 不存在: $appId") }
        val (owner, repo) = githubService.parseRepoInfo(app.repoUrl)

        // 使用 GitHub App 的安装 Token
        val installationId = app.installationId ?: throw IllegalStateException("App 未安装 GitHub App，请先安装")
        val token = gitHubAppService.getInstallationToken(installationId)

        val workflowContent = generateWorkflowYaml(app, flowId)
        val filePath = ".github/workflows/${workflowName}"
        val encodedContent = Base64.getEncoder().encodeToString(workflowContent.toByteArray())

        // 先检查文件是否已存在
        var sha: String? = null
        try {
            val existingContent = getFileContent(owner, repo, filePath, token)
            sha = existingContent.get("sha").asText()
        } catch (e: Exception) {
            // 文件不存在，继续创建
        }

        val updateRequest = mutableMapOf<String, Any>(
            "message" to if (sha != null) "Update workflow: $workflowName" else "Create workflow: $workflowName",
            "content" to encodedContent
        )
        sha?.let { updateRequest["sha"] = it }

        val api = java.net.URL("https://api.github.com/repos/$owner/$repo/contents/$filePath")
        val connection = api.openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "PUT"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        val requestBody = objectMapper.writeValueAsString(updateRequest)
        connection.outputStream.use { it.write(requestBody.toByteArray()) }

        val responseCode = connection.responseCode
        connection.disconnect()

        if (responseCode !in 200..299) {
            throw IllegalStateException("GitHub API 返回错误: $responseCode")
        }

        val workflowId = workflowName.removeSuffix(".yml").removeSuffix(".yaml")
        app.workflowId = workflowId
        app.updatedAt = java.time.LocalDateTime.now()
        appRepository.save(app)

        return workflowId
    }

    private fun getFileContent(owner: String, repo: String, path: String, token: String): com.fasterxml.jackson.databind.JsonNode {
        val api = java.net.URL("https://api.github.com/repos/$owner/$repo/contents/$path")
        val connection = api.openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

        val responseCode = connection.responseCode
        if (responseCode == 404) {
            throw NoSuchElementException("文件不存在")
        }
        if (responseCode !in 200..299) {
            throw IllegalStateException("获取文件失败: $responseCode")
        }

        val response = connection.inputStream.bufferedReader().readText()
        connection.disconnect()
        return objectMapper.readTree(response)
    }
}