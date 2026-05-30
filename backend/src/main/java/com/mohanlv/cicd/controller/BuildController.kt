package com.mohanlv.cicd.controller

import com.mohanlv.cicd.service.BuildService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/builds")
class BuildController(
    private val buildService: BuildService
) {
    @PostMapping("/trigger/{appId}")
    fun triggerBuild(
        @PathVariable appId: Long,
        @RequestParam(required = false) flowId: Long?,
        @RequestBody(required = false) buildParams: Map<String, String>?
    ): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(buildService.triggerBuild(appId, flowId, buildParams))
        } catch (e: NoSuchElementException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "错误")))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "错误")))
        }
    }

    @GetMapping("/app/{appId}")
    fun getBuildRecords(
        @PathVariable appId: Long,
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<Any> {
        return buildService.getBuildRecords(appId, pageable).map { record ->
            mapOf(
                "id" to record.id,
                "buildNumber" to record.buildNumber,
                "status" to record.status,
                "workflowRunId" to record.workflowRunId,
                "artifactUrl" to record.artifactUrl,
                "commitSha" to record.commitSha,
                "commitMessage" to record.commitMessage,
                "startedAt" to record.startedAt,
                "finishedAt" to record.finishedAt,
                "durationSeconds" to record.getDurationSeconds(),
                "createdAt" to record.createdAt
            )
        }
    }

    @GetMapping("/{id}")
    fun getBuildRecord(@PathVariable id: Long): ResponseEntity<Any> {
        val record = buildService.getBuildRecord(id)
        return if (record != null) {
            ResponseEntity.ok(mapOf(
                "id" to record.id,
                "appId" to record.app?.id,
                "buildNumber" to record.buildNumber,
                "status" to record.status,
                "workflowRunId" to record.workflowRunId,
                "artifactUrl" to record.artifactUrl,
                "commitSha" to record.commitSha,
                "commitMessage" to record.commitMessage,
                "logs" to record.logs,
                "startedAt" to record.startedAt,
                "finishedAt" to record.finishedAt,
                "durationSeconds" to record.getDurationSeconds(),
                "createdAt" to record.createdAt
            ))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/{id}/logs")
    fun getBuildLogs(@PathVariable id: Long): ResponseEntity<Any> {
        val record = buildService.getBuildRecord(id)
        return if (record != null) {
            ResponseEntity.ok(mapOf("logs" to (record.logs ?: "")))
        } else {
            ResponseEntity.notFound().build()
        }
    }
}