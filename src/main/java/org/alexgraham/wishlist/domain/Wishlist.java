package org.alexgraham.wishlist.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class Wishlist {
    private static final String ID_DELIMITER = "#";
    private static final int NAME_MAX_LENGTH = 255;

    private final UUID id;
    private final UUID ownerId;
    private final String name;
    private final Instant createdAt;

    private Wishlist(UUID id, UUID ownerId, String name, Instant createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.createdAt = createdAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public UUID id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String name() {
        return name;
    }

    /**
     * Creates a new Wishlist.
     *
     * @param ownerId The id of the owner of this wishlist
     * @param name The name of the wishlist
     * @return A new wishlist
     */
    public static Wishlist create(UUID ownerId, String name) {
        UUID id = createId(ownerId, name);
        return new Wishlist(id, ownerId, name, Instant.now());
    }

    /**
     * Validates the Wishlist.
     *
     * @return An Optional String, which if present, contains the reason the Wishlist is invalid
     */
    Optional<String> validate() {
        if (name == null) {
            return Optional.of("The Wishlist name cannot be null");
        } else if (name.isBlank()) {
            return Optional.of("The Wishlist name must not be blank");
        } else if (name.length() > NAME_MAX_LENGTH) {
            return Optional.of("This Wishlist name must be greater than " + NAME_MAX_LENGTH + " characters long");
        } else {
            return Optional.empty();
        }
    }

    /*
     * Creates a wishlist id deterministically based on the ownerId and name
     */
    private static UUID createId(UUID ownerId, String name) {
        String idInput = ownerId + ID_DELIMITER + name;
        return UUID.nameUUIDFromBytes(idInput.getBytes());
    }
}
