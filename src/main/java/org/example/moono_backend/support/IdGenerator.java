package org.example.moono_backend.support;

import io.hypersistence.tsid.TSID;

public class IdGenerator {
    public static Long generate() {
        return TSID.Factory
                .getTsid()
                .toLong();
    }
}

