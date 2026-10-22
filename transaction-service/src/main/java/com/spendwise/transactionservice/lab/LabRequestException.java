package com.spendwise.transactionservice.lab;

import com.spendwise.common.exception.SpendWiseException;
import org.springframework.http.HttpStatus;

/**
 * Rejections from the lab endpoints, rendered as RFC 7807 problems by the
 * service's GlobalExceptionHandler: 409 when a job is already running or lab
 * data already exists, 400 when a request is too large.
 */
public class LabRequestException extends SpendWiseException {

    private LabRequestException(String errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }

    static LabRequestException jobRunning(String operation) {
        return new LabRequestException("LAB_JOB_RUNNING", HttpStatus.CONFLICT,
                "A lab " + operation.toLowerCase() + " job is still running. "
                        + "Watch GET /lab/seed and retry when its state is COMPLETED or FAILED.");
    }

    static LabRequestException dataPresent(long rows) {
        return new LabRequestException("LAB_DATA_PRESENT", HttpStatus.CONFLICT,
                "Lab data already present (" + rows + " transactions). Run DELETE /lab/seed first, "
                        + "so every seed starts from a known state.");
    }

    static LabRequestException tooLarge(int users, int months) {
        return new LabRequestException("LAB_REQUEST_TOO_LARGE", HttpStatus.BAD_REQUEST,
                String.format("users x months = %d exceeds %d (roughly %.1f million rows). Reduce users or months.",
                        (long) users * months, LabSeedRequest.MAX_USER_MONTHS,
                        LabSeedRequest.MAX_USER_MONTHS * (double) LabDataGenerator.TYPICAL_ROWS_PER_USER_MONTH / 1_000_000));
    }
}
