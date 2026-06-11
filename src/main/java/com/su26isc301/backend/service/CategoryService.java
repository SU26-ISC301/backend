package com.su26isc301.backend.service;

import com.su26isc301.backend.dto.response.CategoryResponse;
import com.su26isc301.backend.entity.Category;
import com.su26isc301.backend.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> getActiveCategoryTree() {
        List<Category> categories = categoryRepository.findByIsActiveTrueOrderByIdAsc();
        Map<Long, CategoryResponse> responseById = new LinkedHashMap<>();
        List<CategoryResponse> roots = new ArrayList<>();

        for (Category category : categories) {
            responseById.put(category.getId(), mapToResponse(category));
        }

        for (Category category : categories) {
            CategoryResponse response = responseById.get(category.getId());
            Long parentId = category.getParent() != null ? category.getParent().getId() : null;

            if (parentId != null && responseById.containsKey(parentId)) {
                responseById.get(parentId).getChildren().add(response);
            } else {
                roots.add(response);
            }
        }

        return roots;
    }

    private CategoryResponse mapToResponse(Category category) {
        Long parentId = category.getParent() != null ? category.getParent().getId() : null;

        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .imageUrl(category.getImageUrl())
                .parentId(parentId)
                .children(new ArrayList<>())
                .build();
    }
}
