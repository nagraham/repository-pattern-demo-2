package org.alexgraham.wishlist.domain;

public interface Repository {

    /**
     * Saves a new Wishlist in persistence.
     *
     * @param wishlist The Wishlist to save.
     * @throws AlreadyExistsException if the Wishlist with the given
     * id already exists.
     */
    void saveNew(Wishlist wishlist);

}
