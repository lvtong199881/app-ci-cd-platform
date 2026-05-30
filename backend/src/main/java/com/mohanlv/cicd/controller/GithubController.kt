package com.mohanlv.cicd.controller

import com.mohanlv.cicd.github.GithubService
import com.mohanlv.cicd.github.GitHubAppService
import com.mohanlv.cicd.service.AppService
import com.mohanlv.cicd.service.WorkflowTemplateService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/github")
class GithubController(
    private val githubService: GithubService,
    private val gitHubAppService: GitHubAppService,
    private val appService: AppService,
    private val workflowTemplateService: WorkflowTemplateService
) {
    @GetMapping("/repos/{appId}/workflows")
    fun listWorkflows(@PathVariable appId: Long): ResponseEntity<Any> {
        val app = appService.getApp(appId) ?: return ResponseEntity.notFound().build()

        val installationId = app.installationId ?: run {
            try {
                gitHubAppService.getInstallationIdByRepo(app.repoUrl)
            } catch (e: Exception) {
                return ResponseEntity.badRequest().body(mapOf("error" to "未找到该仓库的 GitHub App 安装"))
            }
        }

        return try {
            val (owner, repo) = githubService.parseRepoInfo(app.repoUrl)
            val token = gitHubAppService.getInstallationToken(installationId)

            val url = java.net.URL("https://api.github.com/repos/$owner/$repo/actions/workflows")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("Authorization", "Bearer $token")

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return ResponseEntity.status(responseCode).body(mapOf("error" to "获取 workflows 失败: $responseCode"))
            }

            val response = connection.inputStream.bufferedReader().readText()
            val json = com.fasterxml.jackson.databind.ObjectMapper().readTree(response)
            val workflows = json.get("workflows").map {
                mapOf(
                    "id" to it.get("id").asText(),
                    "name" to it.get("name").asText(),
                    "path" to it.get("path").asText()
                )
            }
            ResponseEntity.ok(workflows)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "错误")))
        }
    }

    @GetMapping("/repos/{appId}/workflows/file")
    fun getWorkflowFile(
        @PathVariable appId: Long,
        @RequestParam path: String
    ): ResponseEntity<Any> {
        val app = appService.getApp(appId) ?: return ResponseEntity.notFound().build()

        val installationId = app.installationId ?: run {
            try {
                gitHubAppService.getInstallationIdByRepo(app.repoUrl)
            } catch (e: Exception) {
                return ResponseEntity.badRequest().body(mapOf("error" to "未找到该仓库的 GitHub App 安装"))
            }
        }

        return try {
            val (owner, repo) = githubService.parseRepoInfo(app.repoUrl)
            val token = gitHubAppService.getInstallationToken(installationId)

            val decodedPath = java.net.URLDecoder.decode(path, "UTF-8")
            val filePath = if (decodedPath.startsWith(".github/workflows/")) {
                decodedPath
            } else {
                ".github/workflows/${decodedPath.removeSuffix(".yml").removeSuffix(".yaml")}.yml"
            }

            val api = java.net.URL("https://api.github.com/repos/$owner/$repo/contents/$filePath")
            val connection = api.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("Authorization", "Bearer $token")

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return ResponseEntity.status(responseCode).body(mapOf("error" to "获取文件内容失败: $responseCode"))
            }

            val response = connection.inputStream.bufferedReader().readText()
            val json = com.fasterxml.jackson.databind.ObjectMapper().readTree(response)
            val contentBase64 = json.get("content").asText()
            val content = String(java.util.Base64.getDecoder().decode(contentBase64.replace("\n", "")))
            connection.disconnect()

            ResponseEntity.ok(content)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "错误")))
        }
    }

    @DeleteMapping("/repos/{appId}/workflows")
    fun deleteWorkflow(
        @PathVariable appId: Long,
        @RequestParam path: String
    ): ResponseEntity<Any> {
        val app = appService.getApp(appId) ?: return ResponseEntity.notFound().build()

        val installationId = app.installationId ?: run {
            try {
                gitHubAppService.getInstallationIdByRepo(app.repoUrl)
            } catch (e: Exception) {
                return ResponseEntity.badRequest().body(mapOf("error" to "未找到该仓库的 GitHub App 安装"))
            }
        }

        return try {
            val (owner, repo) = githubService.parseRepoInfo(app.repoUrl)
            val token = gitHubAppService.getInstallationToken(installationId)

            val decodedPath = java.net.URLDecoder.decode(path, "UTF-8")
            val filePath = if (decodedPath.startsWith(".github/workflows/")) {
                decodedPath
            } else {
                ".github/workflows/${decodedPath.removeSuffix(".yml").removeSuffix(".yaml")}.yml"
            }
            val api = java.net.URL("https://api.github.com/repos/$owner/$repo/contents/$filePath")
            val connection = api.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("Authorization", "Bearer $token")

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return ResponseEntity.status(responseCode).body(mapOf("error" to "获取文件信息失败: $responseCode"))
            }

            val response = connection.inputStream.bufferedReader().readText()
            val json = com.fasterxml.jackson.databind.ObjectMapper().readTree(response)
            val sha = json.get("sha").asText()
            connection.disconnect()

            val deleteConnection = api.openConnection() as java.net.HttpURLConnection
            deleteConnection.requestMethod = "DELETE"
            deleteConnection.setRequestProperty("Authorization", "Bearer $token")
            deleteConnection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            deleteConnection.setRequestProperty("Content-Type", "application/json")
            deleteConnection.doOutput = true

            val deleteRequestBody = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                mapOf("message" to "Delete workflow: $filePath", "sha" to sha)
            )
            deleteConnection.outputStream.use { it.write(deleteRequestBody.toByteArray()) }

            val deleteResponseCode = deleteConnection.responseCode
            deleteConnection.disconnect()

            if (deleteResponseCode !in 200..299) {
                return ResponseEntity.status(deleteResponseCode).body(mapOf("error" to "删除 Workflow 失败: $deleteResponseCode"))
            }

            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "错误")))
        }
    }

    @PutMapping("/repos/{appId}/workflows")
    fun updateWorkflow(
        @PathVariable appId: Long,
        @RequestParam path: String,
        @RequestBody workflow: Map<String, Any>
    ): ResponseEntity<Any> {
        val app = appService.getApp(appId) ?: return ResponseEntity.notFound().build()

        val installationId = app.installationId ?: run {
            try {
                gitHubAppService.getInstallationIdByRepo(app.repoUrl)
            } catch (e: Exception) {
                return ResponseEntity.badRequest().body(mapOf("error" to "未找到该仓库的 GitHub App 安装"))
            }
        }

        return try {
            val (owner, repo) = githubService.parseRepoInfo(app.repoUrl)
            val token = gitHubAppService.getInstallationToken(installationId)

            val decodedPath = java.net.URLDecoder.decode(path, "UTF-8")
            val filePath = if (decodedPath.startsWith(".github/workflows/")) {
                decodedPath
            } else {
                ".github/workflows/${decodedPath.removeSuffix(".yml").removeSuffix(".yaml")}.yml"
            }

            val workflowContent = workflowTemplateService.generateWorkflowYaml(app, workflow)
            val encodedContent = java.util.Base64.getEncoder().encodeToString(workflowContent.toByteArray())

            // 获取文件 SHA
            val api = java.net.URL("https://api.github.com/repos/$owner/$repo/contents/$filePath")
            val connection = api.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("Authorization", "Bearer $token")

            val getResponseCode = connection.responseCode
            var sha: String? = null
            if (getResponseCode == 200) {
                val getResponse = connection.inputStream.bufferedReader().readText()
                sha = com.fasterxml.jackson.databind.ObjectMapper().readTree(getResponse).get("sha").asText()
            }
            connection.disconnect()

            val updateRequest = mutableMapOf<String, Any>(
                "message" to "Update workflow: $filePath",
                "content" to encodedContent
            )
            sha?.let { updateRequest["sha"] = it }

            val updateConnection = api.openConnection() as java.net.HttpURLConnection
            updateConnection.requestMethod = "PUT"
            updateConnection.setRequestProperty("Authorization", "Bearer $token")
            updateConnection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            updateConnection.setRequestProperty("Content-Type", "application/json")
            updateConnection.doOutput = true

            val requestBody = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(updateRequest)
            updateConnection.outputStream.use { it.write(requestBody.toByteArray()) }

            val updateResponseCode = updateConnection.responseCode
            updateConnection.disconnect()

            if (updateResponseCode !in 200..299) {
                return ResponseEntity.status(updateResponseCode).body(mapOf("error" to "更新 Workflow 失败: $updateResponseCode"))
            }

            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "错误")))
        }
    }
}