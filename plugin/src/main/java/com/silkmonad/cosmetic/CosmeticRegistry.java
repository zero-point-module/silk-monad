package com.silkmonad.cosmetic;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class CosmeticRegistry {

    private final Map<String, Cosmetic> byId = new LinkedHashMap<>();

    public void register(Cosmetic cosmetic) {
        byId.put(cosmetic.id().toLowerCase(Locale.ROOT), cosmetic);
    }

    public Optional<Cosmetic> get(String id) {
        return Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<Cosmetic> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public Collection<Cosmetic> ofType(CosmeticType type) {
        return byId.values().stream()
                .filter(c -> c.type() == type)
                .collect(Collectors.toUnmodifiableList());
    }

    public int size() {
        return byId.size();
    }

    public void clear() {
        byId.clear();
    }
}
