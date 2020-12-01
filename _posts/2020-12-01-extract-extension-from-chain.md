---
title: Extracting an Extension Function from a Call Chain
layout: post
tags: [Kotlin, Refactoring]
---

Kotlin extension functions let us express our algorithms as functional pipelines.
We can see these as chains of calls, where the output of a stage is fed into the next. 
Here's a chain that reads lines representing `CustomerData` from a `reader`:
   
```kotlin
val valuableCustomers = reader
    .buffered()
    .lineSequence()
    .drop(1) // header
    .map(String::toCustomerData)
    .filter { it.score >= 10 }
    .sortedBy(CustomerData::score)
    .toList()
```

Chains like this are nice and easy to read (at least for English speakers), as we start at the top left and work our way to the bottom right.
This one is long enough to be intimidating though, and mixes concerns at different levels. 
It would be nice to break it into named sections.

We can do that by extracting extension methods that represent parts of the call chain.
In this case we'd like to extract the `drop(1)` to `.filter` lines into a function named `valuableCustomers`. 

IntelliJ doesn't (yet) have a single refactoring that will do that.
We can combine individual refactorings to get there safely though.

First we select from the start of the expression to end of the chain that we want.
In this case, from `reader` to the end of the filter line.

```kotlin
                        reader
    .buffered()
    .lineSequence()
    .drop(1) // header
    .map(String::toCustomerData)
    .filter { it.score >= 10 }
```

"Extract Function", calling it `valuableCustomers`:

```kotlin
val valuableCustomers = valuableCustomers(reader)
    .sortedBy(CustomerData::score)
    .toList()

private fun valuableCustomers(reader: Reader) = reader
    .buffered()
    .lineSequence()
    .drop(1) // header
    .map(String::toCustomerData)
    .filter { it.score >= 10 }
```

Now we select from the start of the extracted function to the beginning of chain that we want.
In this case, from `reader` to the end of `lineSequence()`:

```kotlin
                                                reader
    .buffered()
    .lineSequence()
```

"Introduce Parameter".
As there will be no uses of the old parameter once the new one is there, it shows that `reader` will be deleted. 
Accept the refactor.

That might work, but there is a currently [a bug in IntelliJ](https://youtrack.jetbrains.com/issue/IDEA-256518) that means that in some cases it doesn't.
It doesn't work here, leaving us with both parameters and losing the `buffered().lineSequence()` code altogether:

```kotlin
val valuableCustomers = valuableCustomers(reader)
    .sortedBy(CustomerData::score)
    .toList()

private fun valuableCustomers(reader: Reader, sequence: Sequence<String>) = sequence
    .drop(1) // header
    .map(String::toCustomerData)
    .filter { it.score >= 10 }
```

If this happens, we "Undo" (keeping the `reader` to `lineSequence()` selection) and "Introduce Local Variable":

```kotlin
private fun valuableCustomers(reader: Reader): Sequence<CustomerData> {
    val lineSequence = reader
        .buffered()
        .lineSequence()
    return lineSequence
        .drop(1) // header
        .map(String::toCustomerData)
        .filter { it.score >= 10 }
}
```

Now we select the whole new statement, from `val` to `lineSequence()` and "Introduce Parameter".
This is the point that directly introducing the parameter should have got to:

```kotlin
val valuableCustomers = valuableCustomers(
    reader
        .buffered()
        .lineSequence()
)
    .sortedBy(CustomerData::score)
    .toList()

private fun valuableCustomers(lineSequence: Sequence<String>): Sequence<CustomerData> {
    return lineSequence
        .drop(1) // header
        .map(String::toCustomerData)
        .filter { it.score >= 10 }
}
```

Now Alt-Enter on the `lineSequence` parameter and "Convert parameter to receiver":

```kotlin
val valuableCustomers = reader
    .buffered()
    .lineSequence().valuableCustomers()
    .sortedBy(CustomerData::score)
    .toList()

private fun Sequence<String>.valuableCustomers(): Sequence<CustomerData> {
    return drop(1) // header
        .map(String::toCustomerData)
        .filter { it.score >= 10 }
}
```

Alt-Enter on `return` and "Convert to expression body", and then tidying up the formatting gives us:

```kotlin
val valuableCustomers = reader
    .buffered()
    .lineSequence()
    .valuableCustomers()
    .sortedBy(CustomerData::score)
    .toList()

private fun Sequence<String>.valuableCustomers(): Sequence<CustomerData> = 
    drop(1) // header
    .map(String::toCustomerData)
    .filter { it.score >= 10 }
```    
    
and we're done, at least until JetBrains give us an "Extract extension function" refactor to do it automagically.

Well, almost done.

Refactoring never sleeps, so we do it again and again and again to give us:

```kotlin
val valuableCustomers = reader
    .asLineSequence()
    .valuableCustomers()
    .toListSortedBy(CustomerData::score)

private fun Sequence<String>.valuableCustomers(): Sequence<CustomerData> =
    withoutHeader()
        .map(String::toCustomerData)
        .filter { it.score >= 10 }

fun Reader.asLineSequence() = buffered().lineSequence()

fun <T, R : Comparable<R>> Sequence<T>.toListSortedBy(selector: (T) -> R?) =
    sortedBy(selector)
        .toList()

fun Sequence<String>.withoutHeader() = drop(1)
```

Helpfully the three new extracted functions look like they are more generally applicable, so we've made them public and can extract them into our library of handy extensions.

This refactoring is taken from an example in _Java to Kotlin, A Refactoring Guidebook_, by Nat Pryce and me, due to be published in 2021 by O’Reilly. 
If you like it, you can read more work in progress on [O'Reilly Online Learning](https://learning.oreilly.com/library/view/java-to-kotlin/9781492082262/).
