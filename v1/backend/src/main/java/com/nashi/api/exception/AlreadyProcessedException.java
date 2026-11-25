package com.nashi.api.exception;

import org.springframework.http.HttpStatus;

/**
 * An approve or reject button was pressed for a submission that is no longer PENDING (an old message in the admin
 * chat). Nothing was changed; the message is the answer for the admin.
 */
public class AlreadyProcessedException extends BusinessException {

    public AlreadyProcessedException(Enum<?> status) {
        super("ALREADY_PROCESSED", "Заявку вже оброблено (статус: " + status + ")", HttpStatus.CONFLICT);
    }
}
