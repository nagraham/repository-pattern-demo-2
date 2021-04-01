package org.alexgraham.wishlist.persistence;

import org.alexgraham.wishlist.domain.Item;
import org.alexgraham.wishlist.domain.Wishlist;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A stateless class of static converter functions used for the persistence
 * package (specifically for the DynamoRepository).
 *
 * The mapping logic in this static class is critical to functionality of
 * the repository, because we're essentially defining the schemas of the
 * objects as they are stored in Dynamo.
 *
 * The name "Convert" was chosen explicitly to enhance readability of the
 * static operations:
 *
 *   Convert.toDynamo(item)
 *   Convert.toWishlist(dynamoItem)
 *
 */
public class Convert {

    /**
     * Convert a list of Strings to a Dynamo list of Dynamo Strings.
     *
     * @param stringList list of strings
     * @return an Attribute value of type "l" with a bunch of AttributeValues
     *          of type "s"
     */
    static AttributeValue toDynamo(List<String> stringList) {
        List<AttributeValue> dynamoStringList = stringList.stream()
                .map(str -> AttributeValue.builder().s(str).build())
                .collect(Collectors.toList());
        return AttributeValue.builder().l(dynamoStringList).build();
    }

    /**
     * Convert an wishlist.domain.Item to a Dynamo map structure.
     *
     * @param item the Item to convert
     * @return a dynamo map structure
     */
    static Map<String, AttributeValue> toDynamo(Item item) {
        return Map.of(
                "id", AttributeValue.builder().s(item.id().toString()).build(),
                "description", AttributeValue.builder().s(item.description()).build()
        );
    }

    /**
     * Concert a wishlist.domain.Wishlist to a Dynamo map structure
     *
     * @param wishlist the Wishlist to convert
     * @return a
     */
    static Map<String, AttributeValue> toDynamo(Wishlist wishlist) {
        Map<String, AttributeValue> itemMap = wishlist.items()
                .stream()
                .collect(Collectors.toMap(
                        item -> item.id().toString(),
                        item -> AttributeValue.builder().m(Convert.toDynamo(item)).build())
                );

        List<AttributeValue> itemOrderById = wishlist.items()
                .stream()
                .map(item -> AttributeValue.builder().s(item.id().toString()).build())
                .collect(Collectors.toList());

        return Map.of(
                "id", AttributeValue.builder().s(wishlist.id().toString()).build(),
                "ownerId", AttributeValue.builder().s(wishlist.ownerId().toString()).build(),
                "name", AttributeValue.builder().s(wishlist.name()).build(),
                "createdAt", AttributeValue.builder().s(wishlist.createdAt().toString()).build(),
                "itemMap", AttributeValue.builder().m(itemMap).build(),
                "itemOrderById", AttributeValue.builder().l(itemOrderById).build()
        );
    }

    /**
     * Converts a UUID to a Dynamo partition key.
     *
     * @param id the UUID to convert
     * @param key the partition key name in the schema
     * @return A dynamo mapping with the key (partition key name) and the value
     *         (the identifier for the record).
     */
    static Map<String, AttributeValue> toPartitionKey(UUID id, String key) {
        return Map.of(key, AttributeValue.builder().s(id.toString()).build());
    }

    /**
     * Converts a Dynamo Map structure to an wishlist.domain.Item
     *
     * @param dynamoMap the DynamoMap to convert
     * @return A rehydrated wishlist.domain.Item
     */
    static Item toItem(Map<String, AttributeValue> dynamoMap) {
        return Item.rehydrate(
                UUID.fromString(dynamoMap.get("id").s()),
                dynamoMap.get("description").s()
        );
    }

    /**
     * Converts a list of Dynamo AttributeValues into a list of Strings.
     *
     * @param dynamoList list of AttributeValues
     * @return list of Strings
     */
    static List<String> toStringList(List<AttributeValue> dynamoList) {
        return dynamoList.stream()
                .map(AttributeValue::s)
                .collect(Collectors.toList());
    }

    /**
     * Converts a Dynamo Map structure/record to a wishlist.domain.Wishlist
     *
     * @param dynamoMap the map to convert
     * @return a rehydrated wishlist.domain.Wishlist
     */
    static Wishlist toWishlist(Map<String, AttributeValue> dynamoMap) {
        List<String> idsInOrder = toStringList(dynamoMap.get("itemOrderById").l());

        List<Item> orderedItemList = idsInOrder.stream()
                .map(id -> dynamoMap.get("itemMap").m().get(id))
                .map(itemDynamoMap -> Convert.toItem(itemDynamoMap.m()))
                .collect(Collectors.toList());

        return Wishlist.rehydrate(
                UUID.fromString(dynamoMap.get("id").s()),
                UUID.fromString(dynamoMap.get("ownerId").s()),
                dynamoMap.get("name").s(),
                Instant.parse(dynamoMap.get("createdAt").s()),
                orderedItemList
        );
    }
}
