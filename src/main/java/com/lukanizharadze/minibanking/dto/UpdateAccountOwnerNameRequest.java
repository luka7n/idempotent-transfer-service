package com.lukanizharadze.minibanking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateAccountOwnerNameRequest(
        @NotBlank
        @Size(max = 50)
        String ownerName
) {

}
