package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.AvailabilityRule;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.service.dto.AvailabilityRuleDTO;
import net.jojoaddison.service.dto.ProfessionalDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link AvailabilityRule} and its DTO {@link AvailabilityRuleDTO}.
 */
@Mapper(componentModel = "spring")
public interface AvailabilityRuleMapper extends EntityMapper<AvailabilityRuleDTO, AvailabilityRule> {
    @Mapping(target = "professional", source = "professional", qualifiedByName = "professionalId")
    AvailabilityRuleDTO toDto(AvailabilityRule s);

    @Named("professionalId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    ProfessionalDTO toDtoProfessionalId(Professional professional);
}
