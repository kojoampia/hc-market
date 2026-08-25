package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.Dispute;
import net.jojoaddison.service.dto.DisputeDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Dispute} and its DTO {@link DisputeDTO}.
 */
@Mapper(componentModel = "spring")
public interface DisputeMapper extends EntityMapper<DisputeDTO, Dispute> {}
