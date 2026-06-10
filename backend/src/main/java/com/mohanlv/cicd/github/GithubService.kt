package com.mohanlv.cicd.github

import org.springframework.stereotype.Service
import java.net.HttpURLConnection
import java.net.URL

@Service
class GithubService(
    private val gitHubAppService: GitHubAppService
) {
    private val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()

    fun parseRepoInfo(repoUrl: String): Pair<String, String> {
        val regex = Regex("github\\.com[/:]([^/]+)/([^/]+?)(?:\\.git)?$")
        val match = regex.find(repoUrl) ?: throw IllegalArgumentException("无效的 GitHub 仓库地址")
        return Pair(match.groupValues[1], match.groupValues[2])
    }

    fun triggerWorkflow(
        installationId: String,
        owner: String,
        repo: String,
        workflowFileName: String,
        ref: String,
        inputs: Map<String, String>
    ): Map<String, Any> {
        val token = gitHubAppService.getInstallationToken(installationId)

        // workflowFileName 可以是文件名（如 release-build.yml）或数字 ID
        val url = URL("https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowFileName/dispatches")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        val requestBody = objectMapper.writeValueAsString(mapOf(
            "ref" to ref,
            "inputs" to inputs
        ))
        connection.outputStream.use { it.write(requestBody.toByteArray()) }

        val responseCode = connection.responseCode
        val responseBody = connection.errorStream?.bufferedReader()?.readText() ?: connection.inputStream?.bufferedReader()?.readText() ?: ""
        connection.disconnect()

        if (responseCode !in 200..299) {
            throw IllegalStateException("触发 Workflow 失败: $responseCode, body: $responseBody, url: $url")
        }

        // 获取刚创建的 run
        val runsUrl = URL("https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowFileName/runs?per_page=1")
        val runsConnection = runsUrl.openConnection() as HttpURLConnection
        runsConnection.setRequestProperty("Authorization", "Bearer $token")
        runsConnection.setRequestProperty("Accept", "application/vnd.github.v3+json")

        val runsResponse = runsConnection.inputStream.bufferedReader().readText()
        val runsJson = objectMapper.readTree(runsResponse)
        val firstRun = runsJson.get("workflow_runs").first()
        val runId = firstRun.get("id").asLong()
        val headSha = firstRun.get("head_sha").asText()
        runsConnection.disconnect()

        return mapOf(
            "owner" to owner,
            "repo" to repo,
            "runId" to runId,
            "headSha" to headSha
        )
    }

    fun getWorkflowRun(installationId: String, owner: String, repo: String, runId: Long): Map<String, Any>? {
        val token = gitHubAppService.getInstallationToken(installationId)

        val url = URL("https://api.github.com/repos/$owner/$repo/actions/runs/$runId")
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            return null
        }

        val response = connection.inputStream.bufferedReader().readText()
        connection.disconnect()

        val json = objectMapper.readTree(response)
        return mapOf(
            "id" to json.get("id").asLong(),
            "name" to json.get("name").asText(),
            "status" to json.get("status").asText(),
            "conclusion" to (json.get("conclusion")?.asText() ?: ""),
            "htmlUrl" to json.get("html_url").asText(),
            "runNumber" to json.get("run_number").asInt(),
            "headBranch" to json.get("head_branch").asText(),
            "headSha" to json.get("head_sha").asText(),
            "createdAt" to json.get("created_at").asText(),
            "updatedAt" to json.get("updated_at").asText(),
            "runStartedAt" to (json.get("run_started_at")?.asText() ?: ""),
            "completedAt" to (json.get("completed_at")?.asText() ?: "")
        )
    }

    fun downloadArtifact(installationId: String, owner: String, repo: String, runId: Long, artifactName: String): String? {
        val token = gitHubAppService.getInstallationToken(installationId)

        val url = URL("https://api.github.com/repos/$owner/$repo/actions/runs/$runId/artifacts")
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            return null
        }

        val response = connection.inputStream.bufferedReader().readText()
        connection.disconnect()

        val json = objectMapper.readTree(response)
        val artifacts = json.get("artifacts")
        for (artifact in artifacts) {
            if (artifact.get("name").asText() == artifactName) {
                return artifact.get("archive_download_url").asText()
            }
        }
        return null
    }

    fun listBranches(installationId: String, owner: String, repo: String): List<String> {
        val token = gitHubAppService.getInstallationToken(installationId)

        val url = URL("https://api.github.com/repos/$owner/$repo/branches")
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            connection.disconnect()
            return emptyList()
        }

        val response = connection.inputStream.bufferedReader().readText()
        connection.disconnect()

        val json = objectMapper.readTree(response)
        return json.map { it.get("name").asText() }
    }

    fun getRunLogs(installationId: String, owner: String, repo: String, runId: Long): String {
        val token = gitHubAppService.getInstallationToken(installationId)

        val logsUrl = URL("https://api.github.com/repos/$owner/$repo/actions/runs/$runId/logs")
        val connection = logsUrl.openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

        return try {
            val responseCode = connection.responseCode
            if (responseCode == 302) {
                return "日志需要从 GitHub 下载，请访问 GitHub Actions 页面查看"
            }
            // GitHub 返回 zip 压缩包
            val zipBytes = connection.inputStream.readBytes()
            connection.disconnect()

            // 解压 zip
            val zipInputStream = java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zipBytes))
            val logs = StringBuilder()
            var entry = zipInputStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".txt")) {
                    val content = zipInputStream.bufferedReader().readText()
                    logs.appendLine("=== ${entry.name} ===")
                    logs.appendLine(content)
                }
                entry = zipInputStream.nextEntry
            }
            logs.toString().ifEmpty { "暂无日志" }
        } catch (e: Exception) {
            "无法获取日志: ${e.message}"
        }
    }

    fun getReleaseAssetUrl(installationId: String, owner: String, repo: String, runId: Long, assetNamePattern: String): String? {
        val token = gitHubAppService.getInstallationToken(installationId)

        // 获取 workflow run 信息获取 run number
        val runInfo = getWorkflowRun(installationId, owner, repo, runId) ?: return null
        val runNumber = runInfo["runNumber"] as? Int ?: return null

        // 获取最新的 release
        val url = URL("https://api.github.com/repos/$owner/$repo/releases")
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            connection.disconnect()
            return null
        }

        val response = connection.inputStream.bufferedReader().readText()
        connection.disconnect()

        val json = objectMapper.readTree(response)
        for (release in json) {
            val tagName = release.get("tag_name").asText()
            // 尝试匹配 tag包含 run number
            if (tagName.contains(runNumber.toString())) {
                val assets = release.get("assets")
                for (asset in assets) {
                    val name = asset.get("name").asText()
                    if (name.contains(assetNamePattern) || name.endsWith(".apk")) {
                        return asset.get("browser_download_url").asText()
                    }
                }
            }
        }
        return null
    }
}