package com.spendwise.transactionservice.domain;

import java.math.BigDecimal;

/**
 * One aggregated row of a user's month: a label (category name or merchant),
 * the transaction type, the summed amount and how many transactions it covers.
 */
public interface SpendTotal {

    String getLabel();

    String getType();

    BigDecimal getTotal();

    Long getEntries();
}
