package com.mohanlv.cicd.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "app_info")
data class AppInfo(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(name = "app_name", length = 100, nullable = false)
    var appName: String = "",

    @Column(name = "app_key", length = 50, nullable = false, unique = true)
    var appKey: String = "",

    @Column(name = "repo_url", length = 255, nullable = false)
    var repoUrl: String = "",

    @Column(name = "branch", length = 50, nullable = false)
    var branch: String = "main",

    @Column(name = "build_config", columnDefinition = "TEXT")
    var buildConfig: String? = null,

    @Column(name = "workflow_id", length = 50)
    var workflowId: String? = null,

    @Column(name = "installation_id", length = 50)
    var installationId: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @JsonIgnore
    @OneToMany(mappedBy = "app", cascade = [CascadeType.ALL], orphanRemoval = true)
    val buildRecords: MutableList<BuildRecord> = mutableListOf(),

    @JsonIgnore
    @OneToMany(mappedBy = "app", cascade = [CascadeType.ALL], orphanRemoval = true)
    val buildFlows: MutableList<BuildFlow> = mutableListOf()
)