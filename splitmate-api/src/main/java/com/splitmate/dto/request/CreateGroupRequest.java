package com.splitmate.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateGroupRequest {

    @NotBlank(message = "Group name is required")
    @Size(max = 100, message = "Group name must not exceed 100 characters")
    private String name;
}
