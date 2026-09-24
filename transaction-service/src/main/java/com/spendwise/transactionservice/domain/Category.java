package com.spendwise.transactionservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "system_default", nullable = false)
    private boolean systemDefault;

    protected Category() {
        // required by JPA
    }

    public Category(String name, boolean systemDefault) {
        this.name = name;
        this.systemDefault = systemDefault;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isSystemDefault() {
        return systemDefault;
    }
}
