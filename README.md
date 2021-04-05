# Repository Pattern Demo

This package is a demonstration of my vision of the Repository Pattern (influenced with flavors from domain-driven design and the ports and adapters architecture pattern).

One of the main demonstrations this code tries to convey is the effectiveness of the Repository Pattern as the code changes over time (e.g. as more features are added, as requirements change). Each change is maintained in its own branch, which builds on the previous branch in the series.

1. **Creating Wishlists:** [01_create_wishlist](https://github.com/nagraham/repository-pattern-demo-2/tree/01_create_wishlist)
2. **Getting a Wishlist by ID:** [02_get_wishlist_by_id](https://github.com/nagraham/repository-pattern-demo-2/tree/02_get_wishlist_by_id)
3. **Add Items to a Wishlist** [03_add_wishlist_item](https://github.com/nagraham/repository-pattern-demo-2/tree/03_add_wishlist_item)
4. **Reorder Items in a Wishlist** [04_reorder_wishlist_items](https://github.com/nagraham/repository-pattern-demo-2/tree/04_reorder_wishlist_items)

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

We sure can! ~~We can leverage Dynamo's [Update Expressions](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Expressions.UpdateExpressions.html) feature to insert individual `Item` records into a datastructure on the `Wishlist` Dynamo record. Instead of reading and writing the entire Wishlist record, we only need to write the much smaller `Item`. Let's say on average, each `Item` is less than 1KB.~~

--- 
**UPDATE**: *I was wrong about UpdateItem and UpdateExpressions. Even if you only update a small portion of a Dynamo item, you pay for the entire size of the Item (Dynamo has to read/write the whole thing on the backend). From the [DynamoDB Docs](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/ProvisionedThroughput.html):*

> UpdateItem modifies a single item in the table. DynamoDB considers the size of the item as it appears before and after the update. The provisioned throughput consumed reflects the larger of these item sizes. Even if you update just a subset of the item's attributes, UpdateItem will still consume the full amount of provisioned throughput (the larger of the "before" and "after" item sizes).

*An alternative design to achieve this goal would be to write Items to Dynamo with the same partition key as the Wishlist, and a sort key set as a string with some marker that tags it as a WishlistItem concatenated with the Item's unique id. Something like `WISHLIST_ITEM#<ITEM_ID>`. Wishlists would also need a sort key (e.g. a string like `WISHLIST_DATA`). The Wishlist order list would be its own record.*

*This is a silly mistake, but it shouldn't diminish the overall point of this demo: the repo pattern keeps code simple by holding all of these messy repository details in a single place.*

---

- **Write Item**: $3,250/mo `= 1KB * 2592000000 * ($1.25 / 1000000)`

That's over a 10x decrease in cost! Besides cost, we will have also improved performance. We've cut down our network calls from 2 to 1, and significantly reduced how much data needs to go over the wire. Also,  Dyanmo tends to throw a lot of 5xx's at high load, which requires quite a few retries, which the SDK typically handles -- *just be sure to use Dynamo's special client configuration*. However, even with Dynamo's robust retry config, it can significantly impact tail latencies (p99.9 and p99.99). Cutting required calls by *half* is a significant win.

>  SIDE BAR: This solution introduces new problems: how will we be able to updates to these Items AND how will we maintain/change the order of the items? This commit does begin to address it, but I'll go into detail in the next section.

Unfortunately, the Dynamo enhanced client does not support Update Expressions (see this [git issue](https://github.com/aws/aws-sdk-java-v2/issues/2292)). So I decided to refactor the entire persistence package to use the basic SDK client.

In the end, this change **vindicates the power of the repository pattern along two major axis of change/complexity**:

1. All the messy details of the interactions with Dynamo (via update expressions) are completely encapsulated in the `DynamoRepository`, and hidden from the domain. If you are coding in the `WishlistService`, you don't really need to be aware of these details at all -- this is a blissful reduction in cognitive load.
2. Even though we completely refactored how we use Dynamo (by switching clients), almost completely re-writing the entire package, the domain was not impacted by it at all.


### 4) Reordering Items in a Wishlist

This builds on change three. We are saving the Items in a map object in the Wishlist. A Map does not allow ordering based on index (like a list), so we need a second complimentary structure to handle ordering for us. In this case, a list of Item ids, which maintains the order the Items should appear in. 

To reorder an Item, we get this list of IDs from the Wishlist's record in Dynamo, remove the Item's ID, re-insert the ID back into the list at the desired index, and update the list.

## Tests

Since this is a demo/prototype, I skipped unit tests, and opted for integration tests using Dynamo running from a local Docker container.

To setup, you'll need to have docker, and you'll need to pull down the Dynamo image:

```
$ docker pull amazon/dynamodb-local
```

Then, run tests as usual.