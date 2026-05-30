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
    @PostMapping("/preview/{appId}")
    fun previewWorkflow(
        @PathVariable appId: Long,
        @RequestBody workflow: Map<String, Any>
    ): ResponseEntity<Any> {
        val app = appService.getApp(appId) ?: return ResponseEntity.notFound().build()
        return try {
            val yaml = workflowTemplateService.generateWorkflowYaml(app, workflow)
            ResponseEntity.ok(mapOf("yaml" to yaml))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/create/{appId}")
    fun createWorkflow(
        @PathVariable appId: Long,
        @RequestBody workflow: Map<String, Any>
    ): ResponseEntity<Any> {
        return try {
            val workflowId = workflowTemplateService.createOrUpdateWorkflow(appId, workflow)
            ResponseEntity.ok(mapOf("workflowId" to workflowId))
        } catch (e: NoSuchElementException) {
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }
}