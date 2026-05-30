package com.mohanlv.cicd.repository

import com.mohanlv.cicd.entity.BuildFlow
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface BuildFlowRepository : JpaRepository<BuildFlow, Long> {
    fun findByAppIdAndIsDefaultTrue(appId: Long): Optional<BuildFlow>
    fun findByAppIdOrderByCreatedAtDesc(appId: Long): List<BuildFlow>
}