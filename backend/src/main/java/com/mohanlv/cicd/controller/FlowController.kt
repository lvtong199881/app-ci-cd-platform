package com.mohanlv.cicd.controller

import com.mohanlv.cicd.service.FlowService
import com.mohanlv.cicd.service.CreateFlowRequest
import com.mohanlv.cicd.service.UpdateFlowRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/flows")
class FlowController(
    private val flowService: FlowService
) {
    @GetMapping("/app/{appId}")
    fun listFlows(@PathVariable appId: Long) = flowService.listFlows(appId)

    @GetMapping("/{id}")
    fun getFlow(@PathVariable id: Long): ResponseEntity<Any> {
        val flow = flowService.getFlow(id)
        return if (flow != null) ResponseEntity.ok(flow)
        else ResponseEntity.notFound().build()
    }

    @PostMapping
    fun createFlow(@RequestBody request: CreateFlowRequest) = flowService.createFlow(request.appId, request)

    @PutMapping("/{id}")
    fun updateFlow(@PathVariable id: Long, @RequestBody request: UpdateFlowRequest): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(flowService.updateFlow(id, request))
        } catch (e: NoSuchElementException) {
            ResponseEntity.notFound().build()
        }
    }

    @DeleteMapping("/{id}")
    fun deleteFlow(@PathVariable id: Long) {
        flowService.deleteFlow(id)
    }
}