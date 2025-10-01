package com.fkcoding.PayrollApp.app.service

import com.fkcoding.PayrollApp.app.controller.PayrollResponse
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class CachedPayroll(
    val id: String,
    val data: PayrollResponse,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

@Service
class PayrollCacheService {
    private val cache = ConcurrentHashMap<String, CachedPayroll>()

    fun store(payroll: PayrollResponse): String {
        val id = UUID.randomUUID().toString()
        cache[id] = CachedPayroll(id, payroll)
        return id
    }

    fun retrieve(id: String): CachedPayroll? = cache[id]

    fun clear() = cache.clear()
}