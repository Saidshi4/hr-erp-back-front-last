package com.hic.dto;

import com.hic.model.Employee.EmploymentStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class EmployeeDTO {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    private LocalDate birthDate;
    private String gender;
    private String mobilePhone;

    @Email(message = "Email must be valid")
    private String email;

    private String finNumber;
    private String faceId;
    private String cardId;

    @NotNull(message = "Department is required")
    private Long departmentId;

    private Long positionId;
    private LocalDate hireDate;
    private EmploymentStatus employmentStatus;
    private String fatherName;
    private String area;
    private String shiftType;
}
