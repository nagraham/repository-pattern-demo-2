package org.alexgraham.wishlist.persistence;

import org.alexgraham.wishlist.domain.AlreadyExistsException;
import org.alexgraham.wishlist.domain.Item;
import org.alexgraham.wishlist.domain.Repository;
import org.alexgraham.wishlist.domain.ResourceNotFoundException;
import org.alexgraham.wishlist.domain.UpdateItemCommand;
import org.alexgraham.wishlist.domain.Wishlist;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class DynamoRepository implements Repository {
    private static final Logger logger = LoggerFactory.getLogger(DynamoRepository.class);

    private static final String DYNAMO_EXPRESSION_ERROR_MSG_NOT_FOUND = "The provided expression refers to an " +
            "attribute that does not exist in the item";

    private final String tableName;
    private final DynamoDbClient dynamoDbClient;

    public DynamoRepository(DynamoDbClient dynamoDbClient, String tableName) {
        this.tableName = tableName;
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    public Wishlist getById(UUID wishlistId) {
        GetItemResponse response = getWishlist(wishlistId);
        return Convert.toWishlist(response.item());
    }

    @Override
    public void saveNewItem(UUID wishlistId, Item item) {

        /*
         * newItem: a Dynamo map attribute representing the Item
         * newId: a Dynamo list attribute only containing the new ItemId
         */
        Map<String, AttributeValue> valuesToUpdate = Map.of(
                ":newItem", AttributeValue.builder().m(Convert.toDynamo(item)).build(),
                ":newId", AttributeValue.builder().l(
                        AttributeValue.builder().s(item.id().toString()).build()
                ).build()
        );

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Convert.toPartitionKey(wishlistId, "id"))
                .updateExpression("SET #items.#newId = :newItem, #itemOrderById = list_append(#itemOrderById, :newId)")
                .expressionAttributeNames(Map.of(
                        "#items", "itemMap",
                        "#itemOrderById", "itemOrderById",
                        "#newId", item.id().toString()
                ))
                .expressionAttributeValues(valuesToUpdate)
                .returnValues(ReturnValue.NONE)
                .build();

        try {
            dynamoDbClient.updateItem(updateRequest);
        } catch (DynamoDbException e) {
            if (e.getMessage().startsWith(DYNAMO_EXPRESSION_ERROR_MSG_NOT_FOUND)) {
                throw new ResourceNotFoundException(e);
            }
            logger.error("An unhandled error occurred while calling updateItem on table={} " +
                            "for wishlist={} to insert item.description={}",
                    tableName,
                    wishlistId,
                    item.description(),
                    e);
            throw new RuntimeException(e);
        } catch (SdkServiceException e) {
            logger.error("An unhandled error occurred while calling updateItem on table={} " +
                            "for wishlist={} to insert item.description={}",
                    tableName,
                    wishlistId,
                    item.description(),
                    e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void saveNew(Wishlist wishlist) {
        PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(Convert.toDynamo(wishlist))
                .conditionExpression("attribute_not_exists(id)")
                .returnValues(ReturnValue.NONE)
                .build();

        try {
            dynamoDbClient.putItem(request);
        } catch (ConditionalCheckFailedException e) {
            throw new AlreadyExistsException(e);
        } catch (SdkServiceException e) {
            logger.error("An exception occurred while calling putItem on table={} for wishlist=[name={}, owner={}]",
                    tableName,
                    wishlist.name(),
                    wishlist.ownerId(),
                    e);
            throw new RuntimeException(e);
        }
    }

    // I'm designing this method around the idea that an Update Command may
    // have multiple optional arguments. Probably violating YAGNI (we don't
    // other arguments) but I'm curious how I would do it.
    @Override
    public void updateItem(UpdateItemCommand command) {
        List<String> updateExpressions = new ArrayList<>();
        Map<String, String> updateAttributeNames = new HashMap<>();
        Map<String, AttributeValue> updateAttributeValues = new HashMap<>();

        if (command.newIndex().isPresent()) {
            List<String> itemOrderById = updateItemPosition(
                    command.wishlistId(),
                    command.itemId(),
                    command.newIndex().get());
            updateExpressions.add("SET #itemOrderById = :updatedItemOrderById");
            updateAttributeNames.put("#itemOrderById", "itemOrderById");
            updateAttributeValues.put(":updatedItemOrderById", Convert.toDynamo(itemOrderById));
        }

        if (updateExpressions.isEmpty()) {
            return; // nothing to update
        }

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Convert.toPartitionKey(command.wishlistId(), "id"))
                .updateExpression(Strings.join(updateExpressions, ','))
                .expressionAttributeNames(updateAttributeNames)
                .expressionAttributeValues(updateAttributeValues)
                .build();

        dynamoDbClient.updateItem(updateRequest);
    }

    /*
     * Gets the itemOrderById list of ids from the persisted Wishlist,
     * and updates the given ItemId's position within that list.
     */
    private List<String> updateItemPosition(
            UUID wishlistId,
            UUID itemId,
            int newIndex
    ) {
        GetItemResponse response = getWishlist(
                wishlistId,
                builder -> builder.projectionExpression("itemOrderById"));

        List<String> itemOrderById = Convert.toStringList(response.item().get("itemOrderById").l());
        if (!itemOrderById.remove(itemId.toString())) {
            throw new ResourceNotFoundException("the item [" + itemId.toString()
                    + "] does not exist in wishlist [" + wishlistId.toString());
        }

        // use Math.min() in case the argument is greater than the size of the list
        newIndex = Math.min(newIndex, itemOrderById.size());

        itemOrderById.add(newIndex, itemId.toString());
        return itemOrderById;
    }

    private GetItemResponse getWishlist(UUID wishlistId) {
        return getWishlist(wishlistId, builder -> { /* no op */ });
    }

    /*
     * Helper method to DRY up some basic code for getting a Wishlist.
     *
     * You can customize the request with the requestConsumer callback,
     * by adding attributes to the request (such as expressions).
     */
    private GetItemResponse getWishlist(UUID wishlistId, Consumer<GetItemRequest.Builder> requestConsumer) {
        GetItemRequest.Builder requestBuilder = GetItemRequest.builder()
                .tableName(tableName)
                .key(Convert.toPartitionKey(wishlistId, "id"));

        requestConsumer.accept(requestBuilder);

        GetItemResponse response = dynamoDbClient.getItem(requestBuilder.build());
        if (response.item().isEmpty()) {
            throw new ResourceNotFoundException("not found");
        }

        return response;
    }
}
