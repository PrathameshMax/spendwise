package com.prathmesh.spendwise.userservice.validation;

import com.prathmesh.spendwise.userservice.exception.InvalidSortFieldException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class UserSortValidator {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id",
            "firstName",
            "lastName",
            "email",
            "createdAt",
            "updatedAt"
    );

    public void validate(Sort sort) {

        for (Sort.Order order : sort) {

            if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                throw new InvalidSortFieldException(
                        "Sorting by field '" + order.getProperty() + "' is not allowed"
                );
            }
        }
    }
}
