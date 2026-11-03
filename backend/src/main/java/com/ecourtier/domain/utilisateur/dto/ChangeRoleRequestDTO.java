package com.ecourtier.domain.utilisateur.dto;

import com.ecourtier.domain.utilisateur.entity.Role;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangeRoleRequestDTO {

    @NotNull(message = "Le nouveau rôle est obligatoire")
    private Role role;
}
