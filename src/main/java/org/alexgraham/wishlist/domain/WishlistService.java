package org.alexgraham.wishlist.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.MissingResourceException;
import java.util.UUID;

public class WishlistService {
    private static final Logger logger = LoggerFactory.getLogger(WishlistService.class);

    private Repository repository;

    public WishlistService(Repository repository) {
        this.repository = repository;
    }

    /**
     * Adds a new Item to a Wishlist.
     *
     * @param wishlistId Id of the wishlist to which the item will be added
     * @param itemDescription description of the new item
     * @return The newly added Item
     * @throws IllegalArgumentException if the Item arguments are invalid
     * @throws MissingResourceException if the wishlist does not exist
     */
    public Item addItemToWishlist(UUID wishlistId, String itemDescription) {
        Item item = Item.create(itemDescription);

        if (item.validate().isPresent()) {
            throw new IllegalArgumentException("The item arguments are invalid: " + item.validate().get());
        }

        try {
            repository.saveNewItem(wishlistId, item);
            return item;
        } catch (ResourceNotFoundException e) {
            throw new ResourceNotFoundException("wishlist with id=" + wishlistId.toString());
        } catch (Exception e) {
            logger.error("Something bad happened: ", e);
            throw new RuntimeException("Internal Service Error");
        }
    }

    /**
     * Creates a new Wishlist
     *
     * @param ownerId ID for the user who owns the Wishlist
     * @param wishlistName The name of the wishlist
     * @return The newly created Wishlist
     * @throws IllegalArgumentException if the wishlist arguments are invalid
     * @throws AlreadyExistsException if the wishlist already exists
     */
    public Wishlist createWishlist(UUID ownerId, String wishlistName) {
        Wishlist wishlist = Wishlist.create(ownerId, wishlistName);

        if (wishlist.validate().isPresent()) {
            throw new IllegalArgumentException("The wishlist arguments are invalid: " + wishlist.validate().get());
        }

        try {
            repository.saveNew(wishlist);
        } catch (AlreadyExistsException e) {
            throw new AlreadyExistsException("Wishlist for owner=" + ownerId + " and name="
                    + wishlistName + " already exists");
        } catch (RuntimeException e) {
            logger.error("Something bad happened: ", e);
            throw new RuntimeException("Internal Server Error");
        }

        return wishlist;
    }

    /**
     * Gets a Wishlist with the given identifier.
     *
     * @param wishlistId The wishlist identifier
     * @return The Wishlist
     * @throws MissingResourceException if the wishlist does not exist
     */
    public Wishlist getWishlistById(UUID wishlistId) {
        // TODO: Authorize access
        try {
            return repository.getById(wishlistId);
        } catch (ResourceNotFoundException e) {
            throw new ResourceNotFoundException("wishlist with id=" + wishlistId.toString());
        } catch (Exception e) {
            logger.error("Something bad happened: ", e);
            throw new RuntimeException("Internal Service Error");
        }
    }
}
