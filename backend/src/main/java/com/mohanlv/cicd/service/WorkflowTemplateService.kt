package com.mohanlv.cicd.service

import com.mohanlv.cicd.entity.AppInfo
import com.mohanlv.cicd.github.GitHubAppService
import com.mohanlv.cicd.github.GithubService
import com.mohanlv.cicd.repository.AppRepository
import com.mohanlv.cicd.repository.BuildFlowRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

@Service
class WorkflowTemplateService(
    private val appRepository: AppRepository,
    private val buildFlowRepository: BuildFlowRepository,
    private val githubService: GithubService,
    private val gitHubAppService: GitHubAppService,
    private val objectMapper: ObjectMapper
) {
    fun generateWorkflowYaml(app: AppInfo, workflow: Map<String, Any>): String {
        val workflowName = workflow["name"]?.toString() ?: "App Build"
        val jobsNode = workflow["jobs"]

        // 处理 List 类型（来自 JSON 反序列化）
        if (jobsNode == null || jobsNode !is ArrayNode) {
            val jobsList = jobsNode as? List<*>
            if (jobsList.isNullOrEmpty()) {
                return generateLegacyYaml(app, workflowName)
            }
            return buildString {
                appendLine("name: $workflowName")
                appendLine()
                appendLine("on:")
                appendLine("  workflow_dispatch:")
                appendLine()
                appendLine("jobs:")

                jobsList.forEachIndexed { index, jobNode ->
                    val jobMap = jobNode as? Map<*, *>
                    val jobId = (jobMap?.get("id") as? String) ?: "job-$index"
                    val jobName = (jobMap?.get("name") as? String) ?: jobId
                    val runsOn = (jobMap?.get("runsOn") as? String) ?: "ubuntu-latest"
                    val needsList = jobMap?.get("needs") as? List<*>
                    val stepsList = jobMap?.get("steps") as? List<*>

                    appendLine("  $jobId:")
                    appendLine("    name: $jobName")
                    appendLine("    runs-on: $runsOn")
                    appendLine("    permissions:")
                    appendLine("      contents: write")

                    if (!needsList.isNullOrEmpty()) {
                        val needsStr = needsList.joinToString(", ") { it?.toString() ?: "" }
                        appendLine("    needs: [$needsStr]")
                    }

                    appendLine("    steps:")

                    stepsList?.forEach { step ->
                        val stepMap = step as? Map<*, *>
                        if (stepMap != null) {
                            appendLine(generateStepYamlFromMap(stepMap))
                        }
                    }
                }
            }
        }

        return buildString {
            appendLine("name: $workflowName")
            appendLine()
            appendLine("on:")
            appendLine("  workflow_dispatch:")
            appendLine()
            appendLine("jobs:")

            jobsNode.forEachIndexed { index, jobNode ->
                val jobId = jobNode.get("id")?.asText() ?: "job-$index"
                val jobName = jobNode.get("name")?.asText() ?: jobId
                val runsOn = jobNode.get("runsOn")?.asText() ?: "ubuntu-latest"
                val needsNode = jobNode.get("needs")
                val stepsNode = jobNode.get("steps")

                appendLine("  $jobId:")
                appendLine("    name: $jobName")
                appendLine("    runs-on: $runsOn")
                appendLine("    permissions:")
                appendLine("      contents: write")
                appendLine("      releases: write")

                if (needsNode != null && needsNode.isArray && needsNode.size() > 0) {
                    val needsList = needsNode.map { it.asText() }.joinToString(", ")
                    appendLine("    needs: [$needsList]")
                }

                appendLine("    steps:")

                if (stepsNode != null && stepsNode.isArray) {
                    stepsNode.forEach { stepNode ->
                        appendLine(generateStepYaml(stepNode))
                    }
                }
            }
        }
    }

    private fun generateStepYamlFromMap(stepMap: Map<*, *>): String {
        val stepType = stepMap["type"] as? String ?: ""
        val stepName = (stepMap["name"] as? String) ?: stepType
        val config = stepMap["config"] as? Map<*, *> ?: emptyMap<Any, Any>()

        return when (stepType) {
            "checkout" -> buildString {
                appendLine("        - name: $stepName")
                appendLine("          uses: actions/checkout@v4")
            }
            "setup-jdk" -> buildString {
                val version = config["version"] as? String ?: "17"
                val distribution = config["distribution"] as? String ?: "temurin"
                appendLine("        - name: $stepName")
                appendLine("          uses: actions/setup-java@v4")
                appendLine("          with:")
                appendLine("            distribution: '$distribution'")
                appendLine("            java-version: '$version'")
            }
            "setup-android" -> buildString {
                val version = config["version"] as? String ?: "latest"
                appendLine("        - name: $stepName")
                appendLine("          uses: android-actions/setup-android@v3")
                appendLine("          with:")
                appendLine("            android-version: '$version'")
            }
            "cache" -> buildString {
                val path = config["path"] as? String ?: ".gradle/caches"
                appendLine("        - name: $stepName")
                appendLine("          uses: actions/cache@v4")
                appendLine("          with:")
                appendLine("            path: $path")
                appendLine("            key: \${runner.os}-gradle-\${hashFiles('**/*.lock')}")
            }
            "gradle", "gradle-test" -> buildString {
                val task = config["task"] as? String ?: (if (stepType == "gradle-test") "test" else "assembleRelease")
                appendLine("        - name: $stepName")
                appendLine("          run: ./gradlew $task")
                appendLine("          shell: bash")
            }
            "shell" -> buildString {
                val script = config["script"] as? String ?: ""
                appendLine("        - name: $stepName")
                appendLine("          run: $script")
                appendLine("          shell: bash")
            }
            "create-release" -> buildString {
                val files = config["files"] as? String ?: "app/build/outputs/apk/release/*.apk"
                appendLine("        - name: $stepName")
                appendLine("          run: |")
                appendLine("            for f in $files; do")
                appendLine("              if [ -f \"\$f\" ]; then")
                appendLine("                dir=\$(dirname \"\$f\")")
                appendLine("                base=\$(basename \"\$f\" .apk)")
                appendLine("                mv \"\$f\" \"\$dir/\${base}-run\${{ github.run_number }}.apk\"")
                appendLine("              fi")
                appendLine("            done")
                appendLine("          shell: bash")
                appendLine("        - name: Upload Release")
                appendLine("          uses: softprops/action-gh-release@v2")
                appendLine("          with:")
                appendLine("            files: app/build/outputs/apk/release/*-run\${{ github.run_number }}.apk")
                appendLine("            tag_name: release-\${{ github.run_number }}")
                appendLine("          env:")
                appendLine("            GITHUB_TOKEN: \${{ secrets.GITHUB_TOKEN }}")
            }
            else -> buildString {
                appendLine("        - name: $stepName")
                appendLine("          run: echo 'Unknown step type: $stepType'")
                appendLine("          shell: bash")
            }
        }
    }

    private fun generateStepYaml(stepNode: com.fasterxml.jackson.databind.JsonNode): String {
        val stepType = stepNode.get("type")?.asText() ?: ""
        val stepName = stepNode.get("name")?.asText() ?: stepType
        val config = stepNode.get("config") ?: objectMapper.createObjectNode()

        return when (stepType) {
            "checkout" -> buildString {
                appendLine("        - name: $stepName")
                appendLine("          uses: actions/checkout@v4")
            }
            "setup-jdk" -> buildString {
                val version = config.get("version")?.asText() ?: "17"
                val distribution = config.get("distribution")?.asText() ?: "temurin"
                appendLine("        - name: $stepName")
                appendLine("          uses: actions/setup-java@v4")
                appendLine("          with:")
                appendLine("            distribution: '$distribution'")
                appendLine("            java-version: '$version'")
            }
            "setup-android" -> buildString {
                val version = config.get("version")?.asText() ?: "latest"
                appendLine("        - name: $stepName")
                appendLine("          uses: android-actions/setup-android@v3")
                appendLine("          with:")
                appendLine("            android-version: '$version'")
            }
            "cache" -> buildString {
                val path = config.get("path")?.asText() ?: ".gradle/caches"
                appendLine("        - name: $stepName")
                appendLine("          uses: actions/cache@v4")
                appendLine("          with:")
                appendLine("            path: $path")
                appendLine("            key: \${runner.os}-gradle-\${hashFiles('**/*.lock')}")
            }
            "gradle", "gradle-test" -> buildString {
                val task = config.get("task")?.asText() ?: (if (stepType == "gradle-test") "test" else "assembleRelease")
                appendLine("        - name: $stepName")
                appendLine("          run: ./gradlew $task")
                appendLine("          shell: bash")
            }
            "shell" -> buildString {
                val script = config.get("script")?.asText() ?: ""
                appendLine("        - name: $stepName")
                appendLine("          run: $script")
                appendLine("          shell: bash")
            }
            "create-release" -> buildString {
                val files = config.get("files")?.asText() ?: "app/build/outputs/apk/release/*.apk"
                appendLine("        - name: $stepName")
                appendLine("          uses: softprops/action-gh-release@v2")
                appendLine("          with:")
                appendLine("            files: $files")
                appendLine("            tag_name: release-\${{ github.run_number }}")
                appendLine("          env:")
                appendLine("            GITHUB_TOKEN: \${{ secrets.GITHUB_TOKEN }}")
            }
            else -> buildString {
                appendLine("        - name: $stepName")
                appendLine("          run: echo 'Unknown step type: $stepType'")
                appendLine("          shell: bash")
            }
        }
    }

    @Suppress("UNUSED")
    private fun generateLegacyYaml(app: AppInfo, workflowName: String): String {
        val flowConfig = buildFlowRepository.findByAppIdAndIsDefaultTrue(app.id).orElse(null)?.flowConfig
            ?: """[{"type":"gradle","task":"assembleRelease","name":"构建 APK"}]"""

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
            appendLine("name: $workflowName")
            appendLine()
            appendLine("on:")
            appendLine("  workflow_dispatch:")
            appendLine()
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
        }
    }

    @Transactional
    fun createOrUpdateWorkflow(appId: Long, workflow: Map<String, Any>): String {
        val app = appRepository.findById(appId).orElseThrow { NoSuchElementException("App 不存在: $appId") }
        val (owner, repo) = githubService.parseRepoInfo(app.repoUrl)

        val installationId = app.installationId ?: throw IllegalStateException("App 未安装 GitHub App，请先安装")
        val token = gitHubAppService.getInstallationToken(installationId)

        val workflowNameRaw = workflow["name"]?.toString() ?: "app-build"
        // 文件名只保留英文、数字、连字符和下划线，多个连字符合并为一个
        val workflowFileName = workflowNameRaw
            .replace(Regex("[^a-zA-Z0-9_-]"), "-")
            .lowercase()
            .replace(Regex("-+"), "-")
            .removeSuffix("-")
            .ifEmpty { "workflow" }
        val workflowContent = generateWorkflowYaml(app, workflow)
        val filePath = ".github/workflows/${workflowFileName}.yml"
        val encodedContent = Base64.getEncoder().encodeToString(workflowContent.toByteArray())

        var sha: String? = null
        try {
            val existingContent = getFileContent(owner, repo, filePath, token)
            sha = existingContent.get("sha").asText()
        } catch (e: Exception) {
            // 文件不存在，继续创建
        }

        val updateRequest = mutableMapOf<String, Any>(
            "message" to if (sha != null) "Update workflow: $workflowFileName" else "Create workflow: $workflowFileName",
            "content" to encodedContent
        )
        sha?.let { updateRequest["sha"] = it }

        val api = URL("https://api.github.com/repos/$owner/$repo/contents/$filePath")
        val connection = api.openConnection() as HttpURLConnection
        connection.requestMethod = "PUT"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        val requestBody = objectMapper.writeValueAsString(updateRequest)
        connection.outputStream.use { it.write(requestBody.toByteArray()) }

        val responseCode = connection.responseCode
        val responseBody = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().readText()
        } else {
            connection.errorStream?.bufferedReader()?.readText() ?: ""
        }
        connection.disconnect()

        if (responseCode !in 200..299) {
            throw IllegalStateException("GitHub API 返回错误: $responseCode, body=$responseBody")
        }

        app.workflowId = workflowFileName
        app.updatedAt = java.time.LocalDateTime.now()
        appRepository.save(app)

        return workflowFileName
    }

    private fun getFileContent(owner: String, repo: String, path: String, token: String): com.fasterxml.jackson.databind.JsonNode {
        val api = URL("https://api.github.com/repos/$owner/$repo/contents/$path")
        val connection = api.openConnection() as HttpURLConnection
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