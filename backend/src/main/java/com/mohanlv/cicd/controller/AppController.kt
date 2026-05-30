package com.mohanlv.cicd.controller

import com.mohanlv.cicd.service.AppService
import com.mohanlv.cicd.service.CreateAppRequest
import com.mohanlv.cicd.service.UpdateAppRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/apps")
class AppController(
    private val appService: AppService,
    @Value("\${github.app.id}") private val appId: String
) {
    @GetMapping
    fun listApps() = appService.listApps()

    @GetMapping("/{id}")
    fun getApp(@PathVariable id: Long): ResponseEntity<Any> {
        val app = appService.getApp(id)
        return if (app != null) ResponseEntity.ok(app)
        else ResponseEntity.notFound().build()
    }

    @GetMapping("/key/{appKey}")
    fun getAppByKey(@PathVariable appKey: String): ResponseEntity<Any> {
        val app = appService.getAppByKey(appKey)
        return if (app != null) ResponseEntity.ok(app)
        else ResponseEntity.notFound().build()
    }

    @PostMapping
    fun createApp(@RequestBody request: CreateAppRequest) = appService.createApp(request)

    @PutMapping("/{id}")
    fun updateApp(@PathVariable id: Long, @RequestBody request: UpdateAppRequest): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(appService.updateApp(id, request))
        } catch (e: NoSuchElementException) {
            ResponseEntity.notFound().build()
        }
    }

    @DeleteMapping("/{id}")
    fun deleteApp(@PathVariable id: Long) {
        appService.deleteApp(id)
    }

    @GetMapping("/install")
    fun getInstallUrl(): ResponseEntity<Any> {
        val url = "https://github.com/apps/cicd-app-bot/installations/new?state=pending"
        return ResponseEntity.ok(mapOf("url" to url))
    }
}