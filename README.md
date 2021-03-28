# Repository Pattern Demo

This package is a demonstration of my vision of the Repository Pattern (influenced with flavors from domain-driven design and the ports and adapters architecture pattern).

One of the main demonstrations this code tries to convey is the effectiveness of the Repository Pattern as the code changes over time (e.g. as more features are added, as requirements change). Each change is maintained in its own branch, which builds on the previous branch in the series.

1. **Creating Wishlists:** [01_create_wishlist](https://github.com/nagraham/repository-pattern-demo-2/tree/01_create_wishlist)
2. **Getting a Wishlist by ID:** [02_get_wishlist_by_id](https://github.com/nagraham/repository-pattern-demo-2/tree/02_get_wishlist_by_id)
3. **Add Items to a Wishlist** [03_add_wishlist_item](https://github.com/nagraham/repository-pattern-demo-2/tree/03_add_wishlist_item)

## Summary of Changes

### 1) Creating Wishlists

This commit sets the stage for the package. This is already covered in the blog post, where it is contrasted with the naive approach of directly coupling domain/business logic with the persistence logic.

### 2) Getting a Wishlist by ID

Extends our `wishlist` module by adding an essential feature to get `Wishlist` objects by their ID. We are only extending code here, and so it is a straightforward change.

As I was writing this feature, I did enjoy being able to tackle it in two sub-parts:

1. Write the domain logic first, and make sure it satisfied the requirements of the feature
2. Write the persistence logic that implemented the new interface method `getById`

If I were writing production-quality code (with unit tests, more sub-features, etc), I might have broken this feature out into 5 separate commits, each with its own code review.

1. Write stubbed methods throughout the stack that demonstrated my intended approach. The new API would return a 503/UnsupportedOperationException
2. Implement the domain logic and tests
3. Implement the persistence logic and tests
4. Implement the integration tests
5. A small change to actually hook up the feature and make it work

I doubt any of these code reviews would amount to more than 300 lines of new code. Instead of spending a week writing a 1000 lines of complicated code, and dumping it on my team, only to get critical feedback on the approach that forces me to redo everything, I can get that critical feedback on day one. Instead of spending a whole week to deliver a feature, I can iteratively deliver chunks. Instead of making my teammates weep at the massive code review I sent them, they will revel in the smaller, focused reviews. If I got sick or hit by bus, someone else could jump in a finish the work.

Using patterns like the Repository Pattern does not only impacts the design of the code -- it opens up strategies for writing and pushing that code in an iterative manner, with more iterative feedback from your team.


### 3) Adding Items to a Wishlist

The whole point to Wishlists is to have items in the list. This feature is pretty damn important.

The basic approach would be to just reuse the existing functions on the `Repository`: get the `Wishlist`, add the new `Item`, and save it. Basically, the *domain* would be responsible for everything. The persistence sub-module would not have required *any changes at all*.

This is the KISS (keep it simple, stupid) / YAGNI approach. For new services/products, you should be focused on iterating and delivering new features that solve customer problems, not making your system as perfectly optimized as possible. [You probably aren't Google](https://blog.bradfieldcs.com/you-are-not-google-84912cf44afb), so this is the *smart approach*.

But fuck that, let's have some fun, and over-optimize the shit out of this thing!

Let's say our millions of Wishlist-crazed customers will add Items at an average load of 1000 TPS (about 2.6 billion ops per month). Let's say the average `Wishlist` object, with all of its `Items`, is 10KB. Let's run these numbers against Dynamo's "On Demand" pricing.

- **Read Wishlist**: $1,944/mo  `= ceil(10KB / 4KB) * 2592000000 * ($0.25 / 1000000)`
- **Write Wishlist**: $32,400/mo `= 10KB * 2592000000 * ($1.25 / 1000000)`

Yikes! About $412,128/year. Can we do better?

We sure can! We can leverage Dynamo's [Update Expressions](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Expressions.UpdateExpressions.html) feature to insert individual `Item` records into a datastructure on the `Wishlist` Dynamo record. Instead of reading and writing the entire Wishlist record, we only need to write the much smaller `Item`. Let's say on average, each `Item` is less than 1KB.

- **Write Item**: $3,250/mo `= 1KB * 2592000000 * ($1.25 / 1000000)`

That's over a 10x decrease in cost! Besides cost, we will have also improved performance. We've cut down our network calls from 2 to 1, and significantly reduced how much data needs to go over the wire. Also,  Dyanmo tends to throw a lot of 5xx's at high load, which requires quite a few retries, which can significantly impact tail latencies (p99.9 and p99.99). Thus, cutting call volume by *half* is a significant win.

>  SIDE BAR: This solution introduces new problems: how will we be able to updates to these Items AND how will we maintain/change the order of the items? This commit does begin to address it, but I'll go into detail in the next section.

Unfortunately, the Dynamo enhanced client does not support Update Expressions (see this [git issue](https://github.com/aws/aws-sdk-java-v2/issues/2292)). So I decided to refactor the entire persistence package to use the basic SDK client.

In the end, this change **vindicates the power of the repository pattern along two major axis of change/complexity**:

1. All the messy details of the interactions with Dynamo (via update expressions) are cmopletely encapsulated in the `DynamoRepository`, and hidden from the domain. If you are coding in the `WishlistService`, you don't really need to be aware of these details at all -- this is a blissful reduction in cognitive load.
2. Even though we completely refactored how we use Dynamo (by switching clients), almost completely re-writing the entire package, the domain was not impacted by it at all.


## Tests

Since this is a demo/prototype, I skipped unit tests, and opted for integration tests using Dynamo running from a local Docker container.

To setup, you'll need to have docker, and you'll need to pull down the Dynamo image:

```
$ docker pull amazon/dynamodb-local
```

Then, run tests as usual.