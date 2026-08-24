package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.Booking;
import net.jojoaddison.service.dto.BookingDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Booking} and its DTO {@link BookingDTO}.
 */
@Mapper(componentModel = "spring")
public interface BookingMapper extends EntityMapper<BookingDTO, Booking> {}
