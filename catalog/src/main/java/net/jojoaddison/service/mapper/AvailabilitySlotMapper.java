package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.AvailabilitySlot;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.service.dto.AvailabilitySlotDTO;
import net.jojoaddison.service.dto.ProfessionalDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link AvailabilitySlot} and its DTO {@link AvailabilitySlotDTO}.
 */
@Mapper(componentModel = "spring")
public interface AvailabilitySlotMapper extends EntityMapper<AvailabilitySlotDTO, AvailabilitySlot> {
    @Mapping(target = "professional", source = "professional", qualifiedByName = "professionalId")
    AvailabilitySlotDTO toDto(AvailabilitySlot s);

    @Named("professionalId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    ProfessionalDTO toDtoProfessionalId(Professional professional);
}
