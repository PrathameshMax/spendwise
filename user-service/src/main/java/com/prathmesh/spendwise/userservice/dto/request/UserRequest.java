package com.prathmesh.spendwise.userservice.dto.request;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(
        description = "Request payload used to create or update a User"
)
public class UserRequest {

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 50)
    @Schema(
            description = "Users first name",
            example = "Prathmesh"
    )
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 50)
    @Schema(
            description = "User's last name",
            example = "Padvekar"
    )
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email")
    @Schema(
            description = "User's email address",
            example = "prathamesh@gmail.com"
    )
    private String email;

    @NotBlank(message = "Phone is required")
    @Pattern(
            regexp = "^[6-9]\\d{9}$",
            message = "Invalid mobile number"
    )
    @Schema(
            description = "User's mobile number",
            example = "9876543210"
    )
    private String phone;
}
