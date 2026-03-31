package com.attendance.controller;

import com.attendance.model.Attendance;
import com.attendance.model.Employee;
import com.attendance.repository.AttendanceRepository;
import com.attendance.repository.EmployeeRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Very simple in-memory rate limiter for login
    private final Map<String, Integer> loginAttempts = new ConcurrentHashMap<>();
    private final Map<String, Long> lockoutMap = new ConcurrentHashMap<>();

    // 1. Authentication
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> payload, HttpServletRequest request) {
        String employeeId = payload.get("employeeId");
        String password = payload.get("password");

        if (employeeId == null || employeeId.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Employee ID and Password are required");
        }
        
        employeeId = employeeId.trim().toUpperCase();

        // Rate Limiting Check
        if (lockoutMap.containsKey(employeeId) && System.currentTimeMillis() - lockoutMap.get(employeeId) < 60000) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Too many failed attempts. Try again in a minute.");
        } else if (lockoutMap.containsKey(employeeId)) {
            lockoutMap.remove(employeeId); // Unlock after a minute
            loginAttempts.remove(employeeId);
        }

        Optional<Employee> employeeOpt = employeeRepository.findById(employeeId);
        if (employeeOpt.isPresent() && passwordEncoder.matches(password, employeeOpt.get().getPassword())) {
            // Success - Reset attempts
            loginAttempts.remove(employeeId);
            
            Employee emp = employeeOpt.get();
            
            // Create New Secure Session
            HttpSession session = request.getSession(true);
            session.setAttribute("user", emp);
            
            // Don't leak the password to the frontend
            Employee safeEmp = new Employee();
            safeEmp.setEmployeeId(emp.getEmployeeId());
            safeEmp.setName(emp.getName());
            safeEmp.setRole(emp.getRole());
            
            return ResponseEntity.ok(safeEmp);
        } else {
            // Failed attempt
            int attempts = loginAttempts.getOrDefault(employeeId, 0) + 1;
            loginAttempts.put(employeeId, attempts);
            if (attempts >= 5) {
                lockoutMap.put(employeeId, System.currentTimeMillis());
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Too many failed attempts. Account temporarily locked.");
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Employee ID or Password");
        }
    }

    // 2. Logout
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok().body("Logged out successfully");
    }

    // Helper to get authenticated user
    private Employee getAuthenticatedUser(HttpServletRequest request) {
        return (Employee) request.getSession(false).getAttribute("user");
    }

    // 3. Get Today's Status
    @GetMapping("/attendance/today")
    public ResponseEntity<?> getTodayStatus(HttpServletRequest request) {
        Employee user = getAuthenticatedUser(request);
        LocalDate today = LocalDate.now();
        Optional<Attendance> attendanceOpt = attendanceRepository.findByEmployeeIdAndDate(user.getEmployeeId(), today);
        if (attendanceOpt.isPresent()) {
            return ResponseEntity.ok(attendanceOpt.get());
        } else {
            return ResponseEntity.ok(new HashMap<String, String>() {{
                put("status", "Not Checked-in");
            }});
        }
    }

    // 4. Check-In
    @PostMapping("/attendance/check-in")
    public ResponseEntity<?> checkIn(HttpServletRequest request) {
        Employee user = getAuthenticatedUser(request);
        String employeeId = user.getEmployeeId();

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        // Prevent duplicate check-in
        Optional<Attendance> existing = attendanceRepository.findByEmployeeIdAndDate(employeeId, today);
        if (existing.isPresent()) {
            return ResponseEntity.badRequest().body("Already checked in for today!");
        }

        Attendance attendance = new Attendance();
        attendance.setEmployeeId(employeeId);
        attendance.setDate(today);
        attendance.setCheckInTime(now);
        
        // Mark late if after 9:30 AM
        if (now.isAfter(LocalTime.of(9, 30))) {
            attendance.setStatus("Late");
        } else {
            attendance.setStatus("On Time");
        }

        attendanceRepository.save(attendance);
        return ResponseEntity.ok(attendance);
    }

    // 5. Check-Out
    @PutMapping("/attendance/check-out")
    public ResponseEntity<?> checkOut(HttpServletRequest request) {
        Employee user = getAuthenticatedUser(request);
        String employeeId = user.getEmployeeId();
        
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        Optional<Attendance> existingOpt = attendanceRepository.findByEmployeeIdAndDate(employeeId, today);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Cannot check-out without checking in first.");
        }

        Attendance attendance = existingOpt.get();
        if (attendance.getCheckOutTime() != null) {
            return ResponseEntity.badRequest().body("Already checked out for today!");
        }

        attendance.setCheckOutTime(now);
        attendanceRepository.save(attendance);
        return ResponseEntity.ok(attendance);
    }

    // 6. Employee History
    @GetMapping("/attendance/history")
    public ResponseEntity<?> getHistory(HttpServletRequest request) {
        Employee user = getAuthenticatedUser(request);
        List<Attendance> history = attendanceRepository.findByEmployeeIdOrderByDateDesc(user.getEmployeeId());
        return ResponseEntity.ok(history.stream().map(this::mapAttendanceWithHours).collect(Collectors.toList()));
    }

    // 7. Admin View (All Records - Protected by Interceptor)
    @GetMapping("/admin/attendance")
    public ResponseEntity<?> getAllAttendance() {
        List<Attendance> all = attendanceRepository.findAllByOrderByDateDesc();
        return ResponseEntity.ok(all.stream().map(this::mapAttendanceWithHours).collect(Collectors.toList()));
    }

    // 8. CSV Export (Admin only)
    @GetMapping(value = "/admin/attendance/export", produces = "text/csv")
    public ResponseEntity<String> exportAttendanceCsv() {
        List<Attendance> all = attendanceRepository.findAllByOrderByDateDesc();
        StringBuilder csv = new StringBuilder("ID,Employee Name,Date,Check In,Check Out,Status,Total Hrs\n");
        
        for (Attendance a : all) {
            Map<String, Object> map = mapAttendanceWithHours(a);
            csv.append(map.get("employeeId")).append(",");
            csv.append("\"").append(map.get("employeeName")).append("\",");
            csv.append(map.get("date")).append(",");
            csv.append(map.get("checkInTime") != null ? map.get("checkInTime") : "-").append(",");
            csv.append(map.get("checkOutTime") != null ? map.get("checkOutTime") : "-").append(",");
            csv.append(map.get("status")).append(",");
            csv.append(map.get("totalHours")).append("\n");
        }
        
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"attendance_report.csv\"")
            .body(csv.toString());
    }
    
    // Helper method to calculate hours
    private Map<String, Object> mapAttendanceWithHours(Attendance attendance) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", attendance.getId());
        map.put("employeeId", attendance.getEmployeeId());
        
        Employee emp = employeeRepository.findById(attendance.getEmployeeId()).orElse(null);
        map.put("employeeName", emp != null ? emp.getName() : "Unknown");
        
        map.put("date", attendance.getDate());
        map.put("checkInTime", attendance.getCheckInTime());
        map.put("checkOutTime", attendance.getCheckOutTime());
        map.put("status", attendance.getStatus());
        
        // Calculate Total Hours Worked
        if (attendance.getCheckInTime() != null && attendance.getCheckOutTime() != null) {
            Duration duration = Duration.between(attendance.getCheckInTime(), attendance.getCheckOutTime());
            long hours = duration.toHours();
            long minutes = duration.toMinutesPart();
            map.put("totalHours", String.format("%02d:%02d", hours, minutes));
        } else {
            map.put("totalHours", "-");
        }
        
        return map;
    }
}
