package com.invault.inventory.roles;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.invault.inventory.roles.dto.RoleResponseDTO;

@Service
@Transactional(readOnly = true)
public class RoleService {

    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public List<RoleResponseDTO> findAllActive() {
        return roleRepository.findByActiveTrue().stream()
                .sorted(Comparator.comparing(role -> role.getName().name()))
                .map(role -> new RoleResponseDTO(
                        role.getId(),
                        role.getName(),
                        role.getDescription(),
                        true
                ))
                .toList();
    }
}
