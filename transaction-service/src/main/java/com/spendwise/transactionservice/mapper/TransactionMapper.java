package com.spendwise.transactionservice.mapper;

import com.spendwise.transactionservice.api.TransactionResponse;
import com.spendwise.transactionservice.domain.Transaction;
import org.mapstruct.Mapping;
import org.mapstruct.Mapper;

/**
 * Compile-time-checked entity-to-response-DTO mapping (Milestone 8), replacing
 * the hand-written {@code TransactionService.toResponse(Transaction)} this
 * milestone removes. Unlike {@link CategoryMapper}, this one needs explicit
 * {@link Mapping} directives: {@link Transaction} holds a nested
 * {@code Category} association, while {@link TransactionResponse} is a flat
 * record with {@code categoryId}/{@code categoryName} fields — exactly the
 * "flattening a nested property" case MapStruct's dotted {@code source}
 * path exists for, replacing the two manual
 * {@code transaction.getCategory().getId()}/{@code getName()} calls the
 * removed method made by hand.
 */
@Mapper(componentModel = "spring")
public interface TransactionMapper {

    @Mapping(source = "category.id", target = "categoryId")
    @Mapping(source = "category.name", target = "categoryName")
    TransactionResponse toResponse(Transaction transaction);
}
