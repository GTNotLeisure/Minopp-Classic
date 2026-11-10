package com.hfstudio.minopp.item;

import java.util.Objects;
import java.util.UUID;

import com.hfstudio.minopp.util.BlockPos;

public record HandCardBinding(int dimension, BlockPos table, long gameId, UUID bearer, UUID token) {

    public HandCardBinding {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(bearer, "bearer");
        Objects.requireNonNull(token, "token");
    }
}
