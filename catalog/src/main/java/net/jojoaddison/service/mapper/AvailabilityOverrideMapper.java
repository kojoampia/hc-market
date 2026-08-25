package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.AvailabilityOverride;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.service.dto.AvailabilityOverrideDTO;
import net.jojoaddison.service.dto.ProfessionalDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link AvailabilityOverride} and its DTO {@link AvailabilityOverrideDTO}.
 */
@Mapper(componentModel = "spring")
public interface AvailabilityOverrideMapper extends EntityMapper<AvailabilityOverrideDTO, AvailabilityOverride> {
    @Mapping(target = "professional", source = "professional", qualifiedByName = "professionalId")
    AvailabilityOverrideDTO toDto(AvailabilityOverride s);

    @Named("professionalId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    ProfessionalDTO toDtoProfessionalId(Professional professional);
}
