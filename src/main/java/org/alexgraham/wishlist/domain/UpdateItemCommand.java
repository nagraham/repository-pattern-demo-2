package org.alexgraham.wishlist.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * The Command object that contains parameters specifying which Item should
 * be updated, and of which attributes.
 *
 * OPTIONAL:
 * - newIndex
 */
public class UpdateItemCommand {

    private final UUID wishlistId;
    private final UUID itemId;
    private Integer newIndex;

    private UpdateItemCommand(UUID wishlistId, UUID itemId, Integer newIndex) {
        this.wishlistId = wishlistId;
        this.itemId = itemId;
        this.newIndex = newIndex;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID wishlistId;
        private UUID itemId;
        private Integer newIndex;

        private Builder() {}

        public Builder wishlistId(UUID wishlistId) {
            this.wishlistId = wishlistId;
            return this;
        }

        public Builder itemId(UUID itemId) {
            this.itemId = itemId;
            return this;
        }

        public Builder newIndex(int newIndex) {
            this.newIndex = newIndex;
            return this;
        }

        UpdateItemCommand build() {
            return new UpdateItemCommand(wishlistId, itemId, newIndex);
        }
    }

    public UUID wishlistId() {
        return wishlistId;
    }

    public UUID itemId() {
        return itemId;
    }

    public Optional<Integer> newIndex() {
        return Optional.ofNullable(newIndex);
    }

    public Optional<String> validate() {
        String error = null;
        if (wishlistId == null) {
            error = "the wishlist id is missing";
        } else if (itemId == null) {
            error = "the item id is missing";
        } else if (newIndex != null && newIndex < 0) {
            error = "the newIndex is invalid: it should be greater than 0";
        }
        return Optional.ofNullable(error);
    }
}
