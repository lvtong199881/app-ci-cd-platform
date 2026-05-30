package com.mohanlv.cicd.repository

import com.mohanlv.cicd.entity.AppInfo
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface AppRepository : JpaRepository<AppInfo, Long> {
    fun findByAppKey(appKey: String): Optional<AppInfo>
    fun existsByAppKey(appKey: String): Boolean
}