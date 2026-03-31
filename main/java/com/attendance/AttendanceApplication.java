package com.attendance;

import com.attendance.model.Employee;
import com.attendance.repository.EmployeeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class AttendanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AttendanceApplication.class, args);
    }

    @Bean
    public CommandLineRunner dataSeeder(EmployeeRepository employeeRepository, PasswordEncoder encoder) {
        return args -> {
            if (employeeRepository.count() == 0) {
                // Seed Admin User
                Employee admin = new Employee();
                admin.setEmployeeId("ADMIN");
                admin.setName("System Administrator");
                admin.setPassword(encoder.encode("admin123"));
                admin.setRole("ADMIN");
                employeeRepository.save(admin);

                // Seed some dummy test employees
                Employee emp1 = new Employee();
                emp1.setEmployeeId("EMP001");
                emp1.setName("John Doe");
                emp1.setPassword(encoder.encode("password123"));
                emp1.setRole("EMPLOYEE");
                employeeRepository.save(emp1);

                Employee emp2 = new Employee();
                emp2.setEmployeeId("EMP002");
                emp2.setName("Jane Smith");
                emp2.setPassword(encoder.encode("password123"));
                emp2.setRole("EMPLOYEE");
                employeeRepository.save(emp2);

                System.out.println("Dummy secure users seeded successfully!");
            } else {
                // Fix existing dummy users if they don't have passwords from the previous version
                for (Employee emp : employeeRepository.findAll()) {
                    if (emp.getPassword() == null || emp.getPassword().isEmpty()) {
                        if ("ADMIN".equals(emp.getEmployeeId())) {
                            emp.setPassword(encoder.encode("admin123"));
                        } else {
                            emp.setPassword(encoder.encode("password123"));
                        }
                        employeeRepository.save(emp);
                        System.out.println("Updated legacy password for " + emp.getEmployeeId());
                    }
                }
            }
        };
    }
}
