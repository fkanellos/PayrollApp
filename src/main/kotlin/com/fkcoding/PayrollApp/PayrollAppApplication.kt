package com.fkcoding.PayrollApp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan(basePackages = ["com.fkcoding.PayrollApp"])
class PayrollAppApplication

fun main(args: Array<String>) {
    runApplication<PayrollAppApplication>(*args)
}