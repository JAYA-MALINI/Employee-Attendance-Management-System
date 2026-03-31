package com.attendance.repository;

import com.attendance.model.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    
    // Find today's attendance record for a specific employee
    Optional<Attendance> findByEmployeeIdAndDate(String employeeId, LocalDate date);
    
    // Find all attendance records for a specific employee (history)
    List<Attendance> findByEmployeeIdOrderByDateDesc(String employeeId);
    
    // Find all attendance records for an admin view, ordered by date
    List<Attendance> findAllByOrderByDateDesc();
}
