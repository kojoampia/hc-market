package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.ServiceOffering;
import net.jojoaddison.service.dto.ProfessionalDTO;
import net.jojoaddison.service.dto.ServiceOfferingDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link ServiceOffering} and its DTO {@link ServiceOfferingDTO}.
 */
@Mapper(componentModel = "spring")
public interface ServiceOfferingMapper extends EntityMapper<ServiceOfferingDTO, ServiceOffering> {
    @Mapping(target = "professional", source = "professional", qualifiedByName = "professionalId")
    ServiceOfferingDTO toDto(ServiceOffering s);

    @Named("professionalId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    ProfessionalDTO toDtoProfessionalId(Professional professional);
}
