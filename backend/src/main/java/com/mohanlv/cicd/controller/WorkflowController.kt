package com.mohanlv.cicd.controller

import com.mohanlv.cicd.service.WorkflowTemplateService
import com.mohanlv.cicd.service.AppService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/workflows")
class WorkflowController(
    private val workflowTemplateService: WorkflowTemplateService,
    private val appService: AppService
) {
    @GetMapping("/preview/{appId}")
    fun previewWorkflow(
        @PathVariable appId: Long,
        @RequestParam(required = false) flowId: Long?
    ): ResponseEntity<Any> {
        val app = appService.getApp(appId) ?: return ResponseEntity.notFound().build()
        return try {
            val yaml = workflowTemplateService.generateWorkflowYaml(app, flowId)
            ResponseEntity.ok(mapOf("yaml" to yaml))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/create/{appId}")
    fun createWorkflow(
        @PathVariable appId: Long,
        @RequestBody request: CreateWorkflowRequest
    ): ResponseEntity<Any> {
        return try {
            val workflowId = workflowTemplateService.createOrUpdateWorkflow(
                appId,
                request.workflowName,
                request.flowId
            )
            ResponseEntity.ok(mapOf("workflowId" to workflowId))
        } catch (e: NoSuchElementException) {
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }
}

data class CreateWorkflowRequest(
    val workflowName: String = "app-build.yml",
    val flowId: Long? = null
)