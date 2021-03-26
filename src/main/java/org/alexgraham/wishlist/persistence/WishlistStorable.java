package org.alexgraham.wishlist.persistence;

import org.alexgraham.wishlist.domain.Wishlist;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;

@DynamoDbBean
public class WishlistStorable {

    private String id;
    private String ownerId;
    private String name;
    private Instant createdAt;

    public WishlistStorable() {
        // default empty constructor
    }

    public WishlistStorable(String id, String ownerId, String name, Instant createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.createdAt = createdAt;
    }

    static WishlistStorable fromWishlist(Wishlist wishlist) {
        return new WishlistStorable(
                wishlist.id().toString(),
                wishlist.ownerId().toString(),
                wishlist.name(),
                wishlist.createdAt());
    }

    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getOwnerId() {
        return ownerId;
    }
    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

