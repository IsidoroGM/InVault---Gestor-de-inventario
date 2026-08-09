package com.invault.inventory.common.dto;

import java.util.List;

import org.springframework.data.domain.Page;

public record PageResponseDTO<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <T> PageResponseDTO<T> from(Page<T> source) {
        return new PageResponseDTO<>(
                source.getContent(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.isFirst(),
                source.isLast()
        );
    }
}

/*
 * PageResponseDTO keeps the API pagination contract independent from Spring internals.
 */
