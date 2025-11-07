//package com.fkcoding.PayrollApp.app.service
//
//import com.fasterxml.jackson.core.type.TypeReference
//import com.fasterxml.jackson.databind.ObjectMapper
//import com.fkcoding.PayrollApp.app.entity.Client
//import com.fkcoding.PayrollApp.app.entity.Employee
//import com.fkcoding.PayrollApp.app.repository.EmployeeRepository
//import com.fkcoding.PayrollApp.app.repository.ClientRepository
//import org.springframework.boot.ApplicationArguments
//import org.springframework.boot.ApplicationRunner
//import org.springframework.core.io.ResourceLoader
//import org.springframework.stereotype.Service
//import org.springframework.transaction.annotation.Transactional
//
//data class EmployeeJson(
//    val id: String,
//    val name: String,
//    val email: String,
//    val calendar_id: String,
//    val color: String = "#2196F3"
//)
//
//data class ClientJson(
//    val name: String,
//    val price: Double,
//    val employee_price: Double,
//    val company_price: Double
//)
//
//@Service
//class DataLoaderService(
//    private val employeeRepository: EmployeeRepository,
//    private val clientRepository: ClientRepository,
//    private val resourceLoader: ResourceLoader,
//    private val objectMapper: ObjectMapper
//) : ApplicationRunner {
//
//    @Transactional
//    override fun run(args: ApplicationArguments?) {
//        if (employeeRepository.count() == 0L) {
//            println("🔄 Loading initial data from JSON files...")
//            loadEmployeesAndClients()
//            println("✅ Data loading completed!")
//        } else {
//            println("📊 Database already contains data, skipping initial load.")
//        }
//    }
//
//    private fun loadEmployeesAndClients() {
//        // Load employees
//        val employeesResource = resourceLoader.getResource("classpath:data/employees.json")
//        val employeesJson: Map<String, EmployeeJson> = objectMapper.readValue(
//            employeesResource.inputStream,
//            object : TypeReference<Map<String, EmployeeJson>>() {}
//        )
//
//        employeesJson.forEach { (key, employeeJson) ->
//            val employee = Employee(
//                id = employeeJson.id,
//                name = employeeJson.name,
//                email = employeeJson.email,
//                calendarId = employeeJson.calendar_id,
//                color = employeeJson.color
//            )
//            employeeRepository.save(employee)
//            println("👤 Loaded employee: ${employee.name}")
//
//            // Load clients for this employee
//            loadClientsForEmployee(employee.id, key)
//        }
//    }
//
//    private fun loadClientsForEmployee(employeeId: String, employeeKey: String) {
//        try {
//            val clientsResource = resourceLoader.getResource("classpath:data/clients/$employeeKey.json")
//            val clientsJson: Map<String, ClientJson> = objectMapper.readValue(
//                clientsResource.inputStream,
//                object : TypeReference<Map<String, ClientJson>>() {}
//            )
//
//            clientsJson.forEach { (_, clientJson) ->
//                val client = Client(
//                    name = clientJson.name,
//                    price = clientJson.price,
//                    employeePrice = clientJson.employee_price,
//                    companyPrice = clientJson.company_price,
//                    employeeId = employeeId
//                )
//                clientRepository.save(client)
//            }
//
//            println("👥 Loaded ${clientsJson.size} clients for $employeeId")
//
//        } catch (e: Exception) {
//            println("⚠️  No clients file found for employee: $employeeKey")
//        }
//    }
//}