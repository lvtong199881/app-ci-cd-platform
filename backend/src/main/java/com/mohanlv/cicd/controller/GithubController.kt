package com.mohanlv.cicd.controller

import com.mohanlv.cicd.github.GithubService
import com.mohanlv.cicd.service.AppService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/github")
class GithubController(
    private val githubService: GithubService,
    private val appService: AppService
) {
    @GetMapping("/repos/{appId}/workflows")
    fun listWorkflows(@PathVariable appId: Long): ResponseEntity<Any> {
        val app = appService.getApp(appId) ?: return ResponseEntity.notFound().build()
        return try {
            val (owner, repo) = githubService.parseRepoInfo(app.repoUrl)
            // 通过 REST API 获取 workflows
            val url = java.net.URL("https://api.github.com/repos/$owner/$repo/actions/workflows")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("Authorization", "Bearer ${System.getenv("GITHUB_TOKEN") ?: ""}")

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
}