package com.hic.dto;

import com.hic.model.Employee.EmploymentStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EmployeeResponseDTO {
    private Long id;
    private String employeeId;
    private String firstName;
    private String lastName;
    private LocalDate birthDate;
    private String gender;
    private String mobilePhone;
    private String email;
    private String finNumber;
    private String faceId;
    private String faceImageUrl;
    private String cardId;
    private Long departmentId;
    private String departmentName;
    private Long positionId;
    private String positionName;
    private LocalDate hireDate;
    private String fatherName;
    private String area;
    private String shiftType;
    private EmploymentStatus employmentStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
