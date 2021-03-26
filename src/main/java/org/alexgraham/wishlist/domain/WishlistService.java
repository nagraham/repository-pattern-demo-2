package org.alexgraham.wishlist.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class WishlistService {
    private static final Logger logger = LoggerFactory.getLogger(WishlistService.class);

    private Repository repository;

    public WishlistService(Repository repository) {
        this.repository = repository;
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
}
