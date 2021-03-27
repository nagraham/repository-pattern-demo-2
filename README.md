# Repository Pattern Demo

This package is a demonstration of my vision of the Repository Pattern (influenced with flavors from domain-driven design and the ports and adapters architecture pattern).

One of the main demonstrations this code tries to convey is the effectiveness of the Repository Pattern as the code changes over time (e.g. as more features are added, as requirements change). Each change is maintained in its own branch, which builds on the previous branch in the series.

1. **Creating Wishlists:** [01_create_wishlist](https://github.com/nagraham/repository-pattern-demo-2/tree/01_create_wishlist)
2. **Getting a Wishlist by ID:** [02_get_wishlist_by_id](https://github.com/nagraham/repository-pattern-demo-2/tree/02_get_wishlist_by_id)

## Tests

Since this is a demo/prototype, I skipped unit tests, and opted for integration tests using Dynamo running from a local Docker container.

To setup, you'll need to have docker, and you'll need to pull down the Dynamo image:

```
$ docker pull amazon/dynamodb-local
```

Then, run tests as usual.