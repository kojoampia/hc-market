package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.BrokerageConfig;
import net.jojoaddison.service.dto.BrokerageConfigDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link BrokerageConfig} and its DTO {@link BrokerageConfigDTO}.
 */
@Mapper(componentModel = "spring")
public interface BrokerageConfigMapper extends EntityMapper<BrokerageConfigDTO, BrokerageConfig> {}
