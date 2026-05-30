package com.mohanlv.cicd

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class CicdApplication

fun main(args: Array<String>) {
    runApplication<CicdApplication>(*args)
}