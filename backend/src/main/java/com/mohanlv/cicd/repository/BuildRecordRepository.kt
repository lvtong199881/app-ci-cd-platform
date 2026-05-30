package com.mohanlv.cicd.repository

import com.mohanlv.cicd.entity.BuildRecord
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BuildRecordRepository : JpaRepository<BuildRecord, Long> {
    fun findByAppIdOrderByCreatedAtDesc(appId: Long, pageable: Pageable): Page<BuildRecord>
    fun findByWorkflowRunId(workflowRunId: String): BuildRecord?
    fun findTopByAppIdOrderByBuildNumberDesc(appId: Long): BuildRecord?
    fun countByAppId(appId: Long): Long
}