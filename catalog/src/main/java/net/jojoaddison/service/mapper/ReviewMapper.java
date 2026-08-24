package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Review;
import net.jojoaddison.service.dto.ProfessionalDTO;
import net.jojoaddison.service.dto.ReviewDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Review} and its DTO {@link ReviewDTO}.
 */
@Mapper(componentModel = "spring")
public interface ReviewMapper extends EntityMapper<ReviewDTO, Review> {
    @Mapping(target = "professional", source = "professional", qualifiedByName = "professionalId")
    ReviewDTO toDto(Review s);

    @Named("professionalId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    ProfessionalDTO toDtoProfessionalId(Professional professional);
}
