package org.alexgraham.wishlist.domain;

import org.alexgraham.wishlist.persistence.DynamoRepository;
import org.alexgraham.wishlist.persistence.WishlistStorable;
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
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
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

    static DynamoDbEnhancedClient dynamoDbEnhancedClient;
    static DynamoDbTable<WishlistStorable> wishlistStorableDynamoDbTable;

    private WishlistService wishlistService;

    // Set up the Dynamo table once
    @BeforeAll
    static void setupDynamoClients() {
        Integer mappedPort = dynamodb.getMappedPort(DYNAMO_PORT);

        DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("fakeKey", "fakeSecret"))
                )
                .region(Region.US_WEST_2)
                .endpointOverride(URI.create("http://localhost:" + mappedPort))
                .build();

        dynamoDbEnhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();

        wishlistStorableDynamoDbTable = dynamoDbEnhancedClient.table(TABLE_NAME,
                TableSchema.fromBean(WishlistStorable.class));

        wishlistStorableDynamoDbTable.createTable();
    }

    @BeforeEach
    void setup() {
        this.wishlistService = new WishlistService(new DynamoRepository(dynamoDbEnhancedClient, TABLE_NAME));
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

            WishlistStorable storable = wishlistStorableDynamoDbTable.getItem(Key.builder()
                    .partitionValue(wishlist.id().toString())
                    .build());
            assertThat(storable.getId(), is(wishlist.id().toString()));
            assertThat(storable.getName(), is("create a wishlist!"));
            assertThat(storable.getOwnerId(), is(ownerId.toString()));
            assertThat(storable.getCreatedAt().isBefore(Instant.now()), is(true));
            assertThat(storable.getCreatedAt(), is(wishlist.createdAt()));
        }

        @Test
        void whenArgumentsAreValid_wishlistAlreadyExists_throwsAlreadyExistsException() {
            wishlistService.createWishlist(ownerId, "create a wishlist!");

            assertThrows(AlreadyExistsException.class, () -> wishlistService
                    .createWishlist(ownerId, "create a wishlist!"));
        }

        @Test
        void invalidArgument() {
            assertThrows(IllegalArgumentException.class, () -> wishlistService
                    .createWishlist(ownerId, null));
        }
    }
}