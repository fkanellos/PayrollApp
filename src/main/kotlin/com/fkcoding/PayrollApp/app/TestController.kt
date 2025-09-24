package com.fkcoding.PayrollApp.app

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class TestController {
    
    @GetMapping("/hello")
    fun hello(): String {
        return "Hello from Payroll Backend!"
    }
    
    @GetMapping("/health")
    fun health(): String {
        return "Service is healthy!"
    }
}
