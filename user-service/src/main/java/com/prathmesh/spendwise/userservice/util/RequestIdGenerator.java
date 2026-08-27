package com.prathmesh.spendwise.userservice.util;

import java.util.UUID;

public final class RequestIdGenerator {

    private RequestIdGenerator(){

    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }
}
