package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.Payout;
import net.jojoaddison.service.dto.PayoutDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Payout} and its DTO {@link PayoutDTO}.
 */
@Mapper(componentModel = "spring")
public interface PayoutMapper extends EntityMapper<PayoutDTO, Payout> {}
