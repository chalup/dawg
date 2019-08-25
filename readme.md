# Dawg

DAWG stands for a Directed Acyclic Word Graph, a data structure for storing a list of words and querying if the set contains a given word:

```kotlin
val dawg = Dawg.generate(listOf("directed", "acyclic", "word", "graph"))

assertThat("word" in dawg).isTrue()
assertThat("dawg" in dawg).isFalse()
```

Sounds like a pretty limited functionality and you might be wondering why would you want to use a `Dawg` instead of a regular `Set<String>`. The answer is the memory footprint. `Dawg` encodes the Polish dictionary for word games – almost 3M words weighing 40Mb – in 1.5Mb binary file and allows blazing fast query method with no false positives or false negatives.

The API also exposes methods to encode the `Dawg` into binary format and decode it back. The library uses `okio` under the hood, and that dependency is also exposed in the encoding/decoding APIs:

```kotlin
val encodedDawg = Buffer().apply { dawg.encode(this) }
val decodedDawg = Dawg.decode(encodedDawg)
```

If you're curious about how it all works under the hood, check out [the blog post I wrote a couple eons ago](http://chalup.github.io/blog/2012/03/19/dawg-data-structure-in-word-judge/).

## TODO
### Separate artifacts for CLI and library
No need to expose the `JCommander` dependency for the `lib` users.

### Bit packing format
Using 32 bits per node is actually quite wasteful. For example for TWL06 DAWG only 24 bits could be used per node: 2 for flags, 5 for letters (for 26 unique values) and 17 for child index (120223 nodes). For that particular example it would reduce the size of encoded DAWG by 25%. For Polish dictionary it would be 15% reduction.

## Releases
None so far, but when I get my act together and push it to some Maven repo, you'll be able to do this:

```groovy
dependencies {
    compile 'org.chalup:dawg:x.y.z'
}
```

```xml
<dependency>
  <groupId>org.chalup</groupId>
  <artifactId>dawg</artifactId>
  <version>x.y.z</version>
</dependency>
```

## License

    Copyright (C) 2019 Jerzy Chalupski

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
