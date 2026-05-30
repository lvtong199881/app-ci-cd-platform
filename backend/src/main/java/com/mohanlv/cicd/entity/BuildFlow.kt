package com.mohanlv.cicd.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "build_flow")
data class BuildFlow(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_id", nullable = false)
    val app: AppInfo? = null,

    @Column(name = "flow_name", length = 100, nullable = false)
    var flowName: String = "",

    @Column(name = "flow_config", columnDefinition = "TEXT", nullable = false)
    var flowConfig: String = "[]",

    @Column(name = "is_default", nullable = false)
    var isDefault: Boolean = false,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)