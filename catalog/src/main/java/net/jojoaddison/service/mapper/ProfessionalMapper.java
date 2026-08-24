package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.Category;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.service.dto.CategoryDTO;
import net.jojoaddison.service.dto.ProfessionalDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Professional} and its DTO {@link ProfessionalDTO}.
 */
@Mapper(componentModel = "spring")
public interface ProfessionalMapper extends EntityMapper<ProfessionalDTO, Professional> {
    @Mapping(target = "category", source = "category", qualifiedByName = "categoryId")
    ProfessionalDTO toDto(Professional s);

    @Named("categoryId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    CategoryDTO toDtoCategoryId(Category category);
}
