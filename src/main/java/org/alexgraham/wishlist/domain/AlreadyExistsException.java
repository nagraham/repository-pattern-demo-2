package org.alexgraham.wishlist.domain;

public class AlreadyExistsException extends RuntimeException {
    public AlreadyExistsException(String s) {
        super(s);
    }

    public AlreadyExistsException(Throwable t) {
        super(t);
    }
}
