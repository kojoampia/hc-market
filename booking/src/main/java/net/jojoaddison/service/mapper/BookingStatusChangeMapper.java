package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.BookingStatusChange;
import net.jojoaddison.service.dto.BookingDTO;
import net.jojoaddison.service.dto.BookingStatusChangeDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link BookingStatusChange} and its DTO {@link BookingStatusChangeDTO}.
 */
@Mapper(componentModel = "spring")
public interface BookingStatusChangeMapper extends EntityMapper<BookingStatusChangeDTO, BookingStatusChange> {
    @Mapping(target = "booking", source = "booking", qualifiedByName = "bookingId")
    BookingStatusChangeDTO toDto(BookingStatusChange s);

    @Named("bookingId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    BookingDTO toDtoBookingId(Booking booking);
}
