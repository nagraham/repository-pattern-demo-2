package org.alexgraham.wishlist.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An Item in a Wishlist.
 *
 * Right now, we only store a "description" field, but we could easily
 * extend this to contain a richer set of data related to products:
 *  - Product data (productId, SKU, description, etc)
 *  - URL to photo
 *  - Owner provided metadata (comments, desired quantity, etc)
 */
public class Item {
    private static final int MAX_DESCRIPTION_LENGTH = 255;

    private final UUID id;
    private final String description;

    private Item(UUID id, String description) {
        this.id = id;
        this.description = description;
    }

    /**
     * Creates an Item.
     *
     * @param description Something describing the item.
     * @return an Item
     */
    public static Item create(String description) {
        return new Item(UUID.randomUUID(), description);
    }

    /**
     * Re-creates an Item object given a set of raw data for an existing item.
     *
     * @param id Id of an existing item.
     * @param description Something describing the item.
     * @return The Item
     */
    public static Item rehydrate(UUID id, String description) {
        return new Item(id, description);
    }

    public UUID id() {
        return id;
    }

    public String description() {
        return description;
    }

    /**
     * Validates the Item.
     *
     * @return An Optional String, which if present, contains the reason the Wishlist is invalid
     */
    Optional<String> validate() {
        if (description == null) {
            return Optional.of("Details cannot be null");
        } else if (description.isBlank()) {
            return Optional.of("Details must not be blank");
        } else if (description.length() > MAX_DESCRIPTION_LENGTH) {
            return Optional.of("Details must be greater than " + MAX_DESCRIPTION_LENGTH + " characters long");
        } else {
            return Optional.empty();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Item item = (Item) o;
        return Objects.equals(id, item.id) && Objects.equals(description, item.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, description);
    }
}
