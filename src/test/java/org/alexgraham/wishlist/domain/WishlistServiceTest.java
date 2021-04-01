package org.alexgraham.wishlist.domain;

import org.alexgraham.wishlist.persistence.DynamoRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for the Wishlist package.
 *
 * Uses a dockerized local DynamoDB instance for the persistence layer.
 */
@Testcontainers
class WishlistServiceTest {

    static final String TABLE_NAME = "Test-Wishlist-Table";

    static final int DYNAMO_PORT = 8000;

    // static, so shared between tests
    @Container
    static final GenericContainer dynamodb = new GenericContainer("amazon/dynamodb-local:latest")
            .withExposedPorts(DYNAMO_PORT);

    static DynamoDbClient dynamoDbClient;

    private WishlistService wishlistService;

    // Set up the Dynamo table once
    @BeforeAll
    static void setupDynamoClients() {
        Integer mappedPort = dynamodb.getMappedPort(DYNAMO_PORT);

        dynamoDbClient = DynamoDbClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("fakeKey", "fakeSecret"))
                )
                .region(Region.US_WEST_2)
                .endpointOverride(URI.create("http://localhost:" + mappedPort))
                .build();

        dynamoDbClient.createTable(CreateTableRequest.builder()
                .tableName(TABLE_NAME)
                .keySchema(
                        KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build()
                )
                .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build()
                )
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build());
    }

    @BeforeEach
    void setup() {
        this.wishlistService = new WishlistService(new DynamoRepository(dynamoDbClient, TABLE_NAME));
    }

    @Nested
    @DisplayName("AddItemToWishlist")
    class AddItemToWishlist {

        private UUID ownerId;
        private Wishlist wishlist;

        @BeforeEach
        void setupUniqueOwnerId() {
            ownerId = UUID.randomUUID();
            wishlist = wishlistService.createWishlist(ownerId, "create a wishlist!");
        }

        @Test
        void addingValidItem_toEmptyWishlist_returnsTheItem() {
            Item item = wishlistService.addItemToWishlist(wishlist.id(), "test-wishlist-item");

            assertThat(item.description(), is("test-wishlist-item"));
        }

        @Test
        void addingValidItem_toEmptyWishlist_persistsTheItem() {
            Item item = wishlistService.addItemToWishlist(wishlist.id(), "test-wishlist-item");

            Wishlist updatedWishlist = wishlistService.getWishlistById(wishlist.id());
            assertThat(updatedWishlist.items(), hasSize(1));
            assertThat(updatedWishlist.items().get(0), is(item));
        }

        @Test
        void addingValidItems_toWishlistWithItems_persistsTheItem_andMaintainsOrder() {
            Item itemA = wishlistService.addItemToWishlist(wishlist.id(), "item-A");
            Item itemB = wishlistService.addItemToWishlist(wishlist.id(), "item-B");
            Item itemC = wishlistService.addItemToWishlist(wishlist.id(), "item-C");

            Wishlist updatedWishlist = wishlistService.getWishlistById(wishlist.id());
            assertThat(updatedWishlist.items(), hasSize(3));
            assertThat(updatedWishlist.items(), contains(itemA, itemB, itemC));
        }

        @Test
        void addingValidItem_toWishlistThatDoesNotExist_throwsResourceNotFoundException() {
            assertThrows(ResourceNotFoundException.class, () -> wishlistService
                    .addItemToWishlist(UUID.randomUUID(), "this-shouldn't-work!"));
        }

        @Test
        void addingInvalidItem_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> wishlistService
                    .addItemToWishlist(UUID.randomUUID(), null));
        }
    }

        @Nested
    @DisplayName("CreateWishlist")
    class CreateWishlist {

        private UUID ownerId;

        @BeforeEach
        void setupUniqueOwnerId() {
            ownerId = UUID.randomUUID();
        }

        @Test
        void whenArgumentsAreValid_createsAndPersistsWishlist() {
            Wishlist wishlist = wishlistService.createWishlist(ownerId, "create a wishlist!");

            GetItemRequest request = GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of("id", AttributeValue.builder().s(wishlist.id().toString()).build()))
                    .build();
            GetItemResponse response = dynamoDbClient.getItem(request);
            assertThat(response.item().get("id").s(), is(wishlist.id().toString()));
            assertThat(response.item().get("name").s(), is("create a wishlist!"));
            assertThat(response.item().get("ownerId").s(), is(ownerId.toString()));
            assertThat(Instant.parse(response.item().get("createdAt").s()).isBefore(Instant.now()), is(true));
            assertThat(Instant.parse(response.item().get("createdAt").s()), is(wishlist.createdAt()));
            assertThat(wishlist.items(), is(empty()));
        }

        @Test
        void whenArgumentsAreValid_wishlistAlreadyExists_throwsAlreadyExistsException() {
            wishlistService.createWishlist(ownerId, "create a wishlist!");

            assertThrows(AlreadyExistsException.class, () -> wishlistService
                    .createWishlist(ownerId, "create a wishlist!"));
        }

        @Test
        void whenArgumentsAreNotValid_throwIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> wishlistService
                    .createWishlist(ownerId, null));
        }
    }

    @Nested
    @DisplayName("GetWishlistBtId")
    class GetWishlistById {

        @Test
        void whenTheWishlistExists_withoutItems_returnsWishlist() {
            UUID ownerId = UUID.randomUUID();
            Wishlist newWishlist = wishlistService.createWishlist(ownerId, "let's get a wishlist!");

            Wishlist wishlist = wishlistService.getWishlistById(newWishlist.id());

            assertThat(wishlist.ownerId(), is(ownerId));
            assertThat(wishlist.name(), is("let's get a wishlist!"));
            assertThat(wishlist.createdAt().isBefore(Instant.now()), is(true));
        }

        @Test
        void whenTheWishlistDoesNotExist_throwResourceNotFoundException() {
            assertThrows(ResourceNotFoundException.class, () -> wishlistService.getWishlistById(UUID.randomUUID()));
        }
    }

    @Nested
    @DisplayName("reorderWishlistItem")
    class ReorderWishlistItem {

        private Wishlist wishlist;

        private void assertWishlistItemsInOrder(Item... items) {
            Wishlist retrievedWishlist = wishlistService.getWishlistById(wishlist.id());
            assertThat(retrievedWishlist.items(), hasSize(items.length));
            assertThat(retrievedWishlist.items(), contains(items));
        }

        @BeforeEach
        public void createWishlist() {
            wishlist = wishlistService.createWishlist(UUID.randomUUID(), "let's reorder some items!");
        }

        @Nested
        @DisplayName("WishlistWithOneItem")
        class WishlistWithOneItem {

            private Item item;

            @BeforeEach
            void setupWishlistItem() {
                item = wishlistService.addItemToWishlist(wishlist.id(), "a-single-item");
            }

            @Test
            void whenIndexArgZero_itemIsPresent() {
                wishlistService.reorderWishlistItem(wishlist.id(), item.id(), 0);

                assertWishlistItemsInOrder(item);
            }

            @Test
            void whenIndexArgGreaterThanZero_itemIsPresent() {
                wishlistService.reorderWishlistItem(wishlist.id(), item.id(), 42);

                assertWishlistItemsInOrder(item);
            }
        }

        @Nested
        @DisplayName("WishlistWithMultipleItems")
        class WishlistWithMultipleItems {

            private Item itemA;
            private Item itemB;
            private Item itemC;
            private Item itemD;

            @BeforeEach
            void setupItems() {
                itemA = wishlistService.addItemToWishlist(wishlist.id(), "item-A");
                itemB = wishlistService.addItemToWishlist(wishlist.id(), "item-B");
                itemC = wishlistService.addItemToWishlist(wishlist.id(), "item-C");
                itemD = wishlistService.addItemToWishlist(wishlist.id(), "item-D");
            }

            @Test
            void reorderFirstItem_toTheBack_itemsAreInNewOrder() {
                wishlistService.reorderWishlistItem(wishlist.id(), itemA.id(), 3);

                assertWishlistItemsInOrder(itemB, itemC, itemD, itemA);
            }

            @Test
            void reorderFirstItem_toTheMiddle_itemsAreInNewOrder() {
                wishlistService.reorderWishlistItem(wishlist.id(), itemA.id(), 1);

                assertWishlistItemsInOrder(itemB, itemA, itemC, itemD);
            }

            @Test
            void reorderFirstItem_backToFirst_itemsAreTheSameOrder() {
                wishlistService.reorderWishlistItem(wishlist.id(), itemA.id(), 0);

                assertWishlistItemsInOrder(itemA, itemB, itemC, itemD);
            }

            @Test
            void reorderMiddleItem_toTheFront_itemsAreInNewOrder() {
                wishlistService.reorderWishlistItem(wishlist.id(), itemB.id(), 0);

                assertWishlistItemsInOrder(itemB, itemA, itemC, itemD);
            }

            @Test
            void reorderMiddleItem_swapWithOtherMiddle_itemsAreInNewOrder() {
                wishlistService.reorderWishlistItem(wishlist.id(), itemB.id(), 2);

                assertWishlistItemsInOrder(itemA, itemC, itemB, itemD);
            }

            @Test
            void reorderMiddleItem_toTheBack_itemsAreInNewOrder() {
                wishlistService.reorderWishlistItem(wishlist.id(), itemB.id(), 3);

                assertWishlistItemsInOrder(itemA, itemC, itemD, itemB);
            }

            @Test
            void reorderMiddleItem_toOriginalSpot_itemsAreInTheSameOrder() {
                wishlistService.reorderWishlistItem(wishlist.id(), itemB.id(), 1);

                assertWishlistItemsInOrder(itemA, itemB, itemC, itemD);
            }

            @Test
            void reorderLastItem_toTheFront_itemsAreInNewOrder() {
                wishlistService.reorderWishlistItem(wishlist.id(), itemD.id(), 0);

                assertWishlistItemsInOrder(itemD, itemA, itemB, itemC);
            }

            @Test
            void reorderLastItem_toTheMiddle_itemsAreInNewOrder() {
                wishlistService.reorderWishlistItem(wishlist.id(), itemD.id(), 2);

                assertWishlistItemsInOrder(itemA, itemB, itemD, itemC);
            }

            @Test
            void reorderLastItem_backToTheEnd_itemsAreInTheSameOrder() {
                wishlistService.reorderWishlistItem(wishlist.id(), itemD.id(), 3);

                assertWishlistItemsInOrder(itemA, itemB, itemC, itemD);
            }

            @Test
            void lotsOfReordering_addNewItems_allItemsAreInExpectedOrder() {
                wishlistService.reorderWishlistItem(wishlist.id(), itemA.id(), 2); // [B, C, A, D]
                wishlistService.reorderWishlistItem(wishlist.id(), itemD.id(), 0); // [D, B, C, A]
                Item itemE = wishlistService.addItemToWishlist(wishlist.id(), "item-E"); // [D, B, C, A, E]
                wishlistService.reorderWishlistItem(wishlist.id(), itemE.id(), 1); // [D, E, B, C, A]
                wishlistService.reorderWishlistItem(wishlist.id(), itemD.id(), 2); // [E, B, D, C, A]

                assertWishlistItemsInOrder(itemE, itemB, itemD, itemC, itemA);
            }
        }

        @Nested
        @DisplayName("Errors")
        class Errors {

            @Test
            void whenTheWishlistDoesNotExist_throwsResourceNotFoundException() {
                assertThrows(ResourceNotFoundException.class, () ->
                        wishlistService.reorderWishlistItem(UUID.randomUUID(), UUID.randomUUID(), 1));
            }

            @Test
            void whenTheWishlistHasNoItems_throwsResourceNotFoundException() {
                assertThrows(ResourceNotFoundException.class, () ->
                        wishlistService.reorderWishlistItem(wishlist.id(), UUID.randomUUID(), 1));
            }

            @Test
            void whenTheWishlistHasItems_uuidDoesNotMatch_throwsResourceNotFoundException() {
                wishlistService.addItemToWishlist(wishlist.id(), "a-single-item");
                assertThrows(ResourceNotFoundException.class, () ->
                        wishlistService.reorderWishlistItem(wishlist.id(), UUID.randomUUID(), 1));
            }

            @Test
            void whenIndexNegative_throwsIllegalArgumentException() {
                Item item = wishlistService.addItemToWishlist(wishlist.id(), "a-single-item");
                assertThrows(IllegalArgumentException.class, () ->
                        wishlistService.reorderWishlistItem(wishlist.id(), item.id(), -1));
            }
        }
    }
}