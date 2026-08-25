package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.Dispute;
import net.jojoaddison.domain.DisputeStatusChange;
import net.jojoaddison.service.dto.DisputeDTO;
import net.jojoaddison.service.dto.DisputeStatusChangeDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link DisputeStatusChange} and its DTO {@link DisputeStatusChangeDTO}.
 */
@Mapper(componentModel = "spring")
public interface DisputeStatusChangeMapper extends EntityMapper<DisputeStatusChangeDTO, DisputeStatusChange> {
    @Mapping(target = "dispute", source = "dispute", qualifiedByName = "disputeId")
    DisputeStatusChangeDTO toDto(DisputeStatusChange s);

    @Named("disputeId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    DisputeDTO toDtoDisputeId(Dispute dispute);
}
