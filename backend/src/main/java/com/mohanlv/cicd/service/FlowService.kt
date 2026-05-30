package com.mohanlv.cicd.service

import com.mohanlv.cicd.entity.BuildFlow
import com.mohanlv.cicd.repository.BuildFlowRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FlowService(
    private val flowRepository: BuildFlowRepository,
    private val objectMapper: ObjectMapper
) {
    fun listFlows(appId: Long): List<BuildFlow> {
        return flowRepository.findByAppIdOrderByCreatedAtDesc(appId)
    }

    fun getFlow(id: Long): BuildFlow? {
        return flowRepository.findById(id).orElse(null)
    }

    @Transactional
    fun createFlow(appId: Long, request: CreateFlowRequest): BuildFlow {
        val flow = BuildFlow(
            app = com.mohanlv.cicd.entity.AppInfo(id = appId),
            flowName = request.flowName,
            flowConfig = objectMapper.writeValueAsString(request.steps),
            isDefault = request.isDefault
        )
        if (request.isDefault) {
            clearDefaultFlow(appId)
        }
        return flowRepository.save(flow)
    }

    @Transactional
    fun updateFlow(id: Long, request: UpdateFlowRequest): BuildFlow {
        val flow = flowRepository.findById(id).orElseThrow { NoSuchElementException("构建流程不存在: $id") }
        request.flowName?.let { flow.flowName = it }
        request.steps?.let { flow.flowConfig = objectMapper.writeValueAsString(it) }
        request.isDefault?.let {
            if (it) clearDefaultFlow(flow.app?.id ?: 0)
            flow.isDefault = it
        }
        return flowRepository.save(flow)
    }

    @Transactional
    fun deleteFlow(id: Long) {
        flowRepository.deleteById(id)
    }

    private fun clearDefaultFlow(appId: Long) {
        flowRepository.findByAppIdAndIsDefaultTrue(appId).ifPresent {
            it.isDefault = false
            flowRepository.save(it)
        }
    }
}

data class CreateFlowRequest(
    val appId: Long,
    val flowName: String,
    val steps: List<FlowStep>,
    val isDefault: Boolean = false
)

data class UpdateFlowRequest(
    val flowName: String? = null,
    val steps: List<FlowStep>? = null,
    val isDefault: Boolean? = null
)

data class FlowStep(
    val type: String,
    val name: String,
    val config: Map<String, Any> = emptyMap()
)