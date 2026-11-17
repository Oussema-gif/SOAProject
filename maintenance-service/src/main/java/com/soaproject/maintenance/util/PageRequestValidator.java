package com.soaproject.maintenance.util;

import com.soaproject.maintenance.exception.InvalidRequestException;

public final class PageRequestValidator {

    private static final int MAX_PAGE_SIZE = 100;

    private PageRequestValidator() { }

    public static void validate(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException(
                    "page must be non-negative and size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
}
