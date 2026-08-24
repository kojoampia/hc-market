package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.Ledger;
import net.jojoaddison.domain.Payout;
import net.jojoaddison.service.dto.LedgerDTO;
import net.jojoaddison.service.dto.PayoutDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Ledger} and its DTO {@link LedgerDTO}.
 */
@Mapper(componentModel = "spring")
public interface LedgerMapper extends EntityMapper<LedgerDTO, Ledger> {
    @Mapping(target = "payout", source = "payout", qualifiedByName = "payoutId")
    LedgerDTO toDto(Ledger s);

    @Named("payoutId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    PayoutDTO toDtoPayoutId(Payout payout);
}
