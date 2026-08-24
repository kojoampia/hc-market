package net.jojoaddison.service;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.jojoaddison.domain.BookingStatusChange;
import net.jojoaddison.repository.BookingStatusChangeRepository;
import net.jojoaddison.service.dto.BookingStatusChangeDTO;
import net.jojoaddison.service.mapper.BookingStatusChangeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.BookingStatusChange}.
 */
@Service
@Transactional
public class BookingStatusChangeService {

    private static final Logger LOG = LoggerFactory.getLogger(BookingStatusChangeService.class);

    private final BookingStatusChangeRepository bookingStatusChangeRepository;

    private final BookingStatusChangeMapper bookingStatusChangeMapper;

    public BookingStatusChangeService(
        BookingStatusChangeRepository bookingStatusChangeRepository,
        BookingStatusChangeMapper bookingStatusChangeMapper
    ) {
        this.bookingStatusChangeRepository = bookingStatusChangeRepository;
        this.bookingStatusChangeMapper = bookingStatusChangeMapper;
    }

    /**
     * Save a bookingStatusChange.
     *
     * @param bookingStatusChangeDTO the entity to save.
     * @return the persisted entity.
     */
    public BookingStatusChangeDTO save(BookingStatusChangeDTO bookingStatusChangeDTO) {
        LOG.debug("Request to save BookingStatusChange : {}", bookingStatusChangeDTO);
        BookingStatusChange bookingStatusChange = bookingStatusChangeMapper.toEntity(bookingStatusChangeDTO);
        bookingStatusChange = bookingStatusChangeRepository.save(bookingStatusChange);
        return bookingStatusChangeMapper.toDto(bookingStatusChange);
    }

    /**
     * Update a bookingStatusChange.
     *
     * @param bookingStatusChangeDTO the entity to save.
     * @return the persisted entity.
     */
    public BookingStatusChangeDTO update(BookingStatusChangeDTO bookingStatusChangeDTO) {
        LOG.debug("Request to update BookingStatusChange : {}", bookingStatusChangeDTO);
        BookingStatusChange bookingStatusChange = bookingStatusChangeMapper.toEntity(bookingStatusChangeDTO);
        bookingStatusChange = bookingStatusChangeRepository.save(bookingStatusChange);
        return bookingStatusChangeMapper.toDto(bookingStatusChange);
    }

    /**
     * Partially update a bookingStatusChange.
     *
     * @param bookingStatusChangeDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<BookingStatusChangeDTO> partialUpdate(BookingStatusChangeDTO bookingStatusChangeDTO) {
        LOG.debug("Request to partially update BookingStatusChange : {}", bookingStatusChangeDTO);

        return bookingStatusChangeRepository
            .findById(bookingStatusChangeDTO.getId())
            .map(existingBookingStatusChange -> {
                bookingStatusChangeMapper.partialUpdate(existingBookingStatusChange, bookingStatusChangeDTO);

                return existingBookingStatusChange;
            })
            .map(bookingStatusChangeRepository::save)
            .map(bookingStatusChangeMapper::toDto);
    }

    /**
     * Get all the bookingStatusChanges.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<BookingStatusChangeDTO> findAll() {
        LOG.debug("Request to get all BookingStatusChanges");
        return bookingStatusChangeRepository
            .findAll()
            .stream()
            .map(bookingStatusChangeMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one bookingStatusChange by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<BookingStatusChangeDTO> findOne(Long id) {
        LOG.debug("Request to get BookingStatusChange : {}", id);
        return bookingStatusChangeRepository.findById(id).map(bookingStatusChangeMapper::toDto);
    }

    /**
     * Delete the bookingStatusChange by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete BookingStatusChange : {}", id);
        bookingStatusChangeRepository.deleteById(id);
    }
}
