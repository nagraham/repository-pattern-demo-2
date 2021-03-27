package org.alexgraham.wishlist.domain;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(Throwable t) {
        super(t);
    }

}
