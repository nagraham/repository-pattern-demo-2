package org.alexgraham.wishlist.domain;

import java.util.UUID;

public interface Repository {

    /**
     * Retrieves a wishlist from storage.
     *
     * @param wishlistId the ID of the wishlist to get
     * @return A wishlist
     * @throws java.util.ResourceNotFoundException if the Wishlist is not found
     */
    Wishlist getById(UUID wishlistId);

    /**
     * Saves a new Wishlist in persistence.
     *
     * @param wishlist The Wishlist to save.
     * @throws AlreadyExistsException if the Wishlist with the given
     * id already exists.
     */
    void saveNew(Wishlist wishlist);

}
