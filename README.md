# Repository Pattern Demo

This package is a demonstration of my vision of the Repository Pattern (influenced with flavors from domain-driven design and the ports and adapters architecture pattern).

## Tests

Since this is a demo/prototype, I skipped unit tests, and opted for integration tests using Dynamo running from a local Docker container.

To setup, you'll need to have docker, and you'll need to pull down the Dynamo image:

```
$ docker pull amazon/dynamodb-local
```

Then, run tests as usual.