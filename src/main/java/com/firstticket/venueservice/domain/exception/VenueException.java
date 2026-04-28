package com.firstticket.venueservice.domain.exception;

import com.firstticket.common.exception.BusinessException;

public class VenueException extends BusinessException {
    public VenueException(VenueErrorCode errorCode) {
        super(errorCode);
    }
}
