package com.dumble.service.session.domain.mapper;

import com.dumble.service.session.domain.dto.request.BookingCreateRequest;
import com.dumble.service.session.domain.dto.response.BookingResponse;
import com.dumble.service.session.domain.entity.Booking;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookingMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "session", ignore = true)
    @Mapping(target = "participantId", ignore = true)
    @Mapping(target = "paymentStatus", constant = "PENDING")
    @Mapping(target = "amountPaid", ignore = true)
    @Mapping(target = "paymentId", ignore = true)
    @Mapping(target = "transactionRef", ignore = true)
    @Mapping(target = "bookingDate", ignore = true)
    Booking toEntity(BookingCreateRequest request);

    @Mapping(target = "sessionId", source = "session.id")
    @Mapping(target = "sessionTitle", source = "session.title")
    BookingResponse toResponse(Booking booking);
}
