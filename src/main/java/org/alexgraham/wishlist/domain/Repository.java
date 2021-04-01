package org.alexgraham.wishlist.domain;

import java.util.UUID;

public interface Repository {

    /**
     * Retrieves a wishlist from storage.
     *
     * @param wishlistId the ID of the wishlist to get
     * @return A wishlist
     * @throws ResourceNotFoundException if the Wishlist is not found
     */
    Wishlist getById(UUID wishlistId);

    /**
     * Saves a new Item in the persisted Wishlist object.
     *
     * @param wishlistId the ID of the wishlist the Item will be saved to.
     * @param item the Item to save; overwrites the Item if one with the
     *            same ID already exists
     * @throws ResourceNotFoundException if the Wishlist is not found
     */
    void saveNewItem(UUID wishlistId, Item item);

    /**
     * Saves a new Wishlist in persistence.
     *
     * @param wishlist The Wishlist to save.
     * @throws AlreadyExistsException if the Wishlist with the given
     * id already exists.
     */
    void saveNew(Wishlist wishlist);

    /**
     * Updates an Item within a specified Wishlist.
     *
     * @param command A command object that contains parameters for the
     *                update.
     * @throws ResourceNotFoundException if the Wishlist is not found,
     * of if the Item is not found in the Wishlist.
     */
    void updateItem(UpdateItemCommand command);
}
