package com.prathmesh.spendwise.transactionservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(
        description = "Response containing user details"
)
public class UserResponse {

    @Schema(
            description = "Unique ID of the user",
            example = "1"
    )
    private Long id;

    @Schema(
            description = "User's first name",
            example = "Prathamesh"
    )
    private String firstName;

    @Schema(
            description = "User's last name",
            example = "Padavekar"
    )
    private String lastName;

    @Schema(
            description = "User's email address",
            example = "prathamesh@gmail.com"
    )
    private String email;

    @Schema(
            description = "User's mobile number",
            example = "9876543210"
    )
    private String phone;

}
