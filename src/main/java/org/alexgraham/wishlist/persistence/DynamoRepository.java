package org.alexgraham.wishlist.persistence;

import org.alexgraham.wishlist.domain.AlreadyExistsException;
import org.alexgraham.wishlist.domain.Repository;
import org.alexgraham.wishlist.domain.Wishlist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

public class DynamoRepository implements Repository {
    private static final Logger logger = LoggerFactory.getLogger(DynamoRepository.class);

    private final DynamoDbTable<WishlistStorable> wishlistStorableTable;

    public DynamoRepository(DynamoDbEnhancedClient dynamoDbEnhanced, String tableName) {
        this.wishlistStorableTable = dynamoDbEnhanced.table(tableName, TableSchema.fromBean(WishlistStorable.class));
    }

    @Override
    public void saveNew(Wishlist wishlist) {
        try {
            wishlistStorableTable.putItem(PutItemEnhancedRequest.builder(WishlistStorable.class)
                    .item(WishlistStorable.fromWishlist(wishlist))
                    .conditionExpression(Expression.builder()
                            .expression("attribute_not_exists(id)")
                            .build())
                    .build());
        } catch (ConditionalCheckFailedException e) {
            throw new AlreadyExistsException(e);
        } catch (SdkServiceException e) {
            logger.error("An exception occurred while calling putItem on table={} for wishlist=[name={}, owner={}]",
                    wishlistStorableTable.tableName(),
                    wishlist.name(),
                    wishlist.ownerId(),
                    e);
            throw new RuntimeException(e);
        }
    }
}
