package com.mohanlv.cicd.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "build_record")
data class BuildRecord(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_id", nullable = false)
    val app: AppInfo? = null,

    @Column(name = "build_number", nullable = false)
    val buildNumber: Int = 0,

    @Column(name = "status", length = 20, nullable = false)
    var status: String = "pending",

    @Column(name = "workflow_run_id", length = 50)
    var workflowRunId: String? = null,

    @Column(name = "artifact_url", length = 255)
    var artifactUrl: String? = null,

    @Column(name = "commit_sha", length = 40)
    var commitSha: String? = null,

    @Column(name = "commit_message", length = 255)
    var commitMessage: String? = null,

    @Column(name = "started_at")
    var startedAt: LocalDateTime? = null,

    @Column(name = "finished_at")
    var finishedAt: LocalDateTime? = null,

    @Column(name = "logs", columnDefinition = "TEXT")
    var logs: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    fun getDurationSeconds(): Long? {
        if (startedAt == null || finishedAt == null) return null
        return java.time.Duration.between(startedAt, finishedAt).seconds
    }
}