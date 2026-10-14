package com.lukanizharadze.transferservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAccountOwnerNameRequest(
        @NotBlank
        @Size(max = 50)
        String ownerName
) {
}
