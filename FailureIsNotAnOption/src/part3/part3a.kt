/*-
---
title: Failure is not an Option - Functional Error Handling in Kotlin. Part 3 - Result and Fold
layout: post
---
-*/

package part3

import part2.*
import java.io.BufferedReader

/*-
This is Part 3 in a series looking at functional error handling in Kotlin. The parts are

* [Part 1 - Exceptions](failure-is-not-an-option-part-1.html)
* [Part 2 - Either](failure-is-not-an-option-part-2.html)
* [Part 3 - Result and Fold](failure-is-not-an-option-part-3.html)

In this third episode I’ll look at some of the pros and cons of using an Either type for functional error handling.

If you were ahead of the game in the last installment you'll have noticed that we mostly caught exceptions and translated them to a Either.Left to signify failure.
-*/

object A {
//`
fun parseInt(s: String): Either<String, Int> = try {
    Right(Integer.parseInt(s))
} catch (exception: Exception) {
    Left(exception.message ?: "No message")
}
//`
}

/*-
You may further have said to yourself that it seemed a shame to loose all that nice stack-tracey information that Java went out of it's way to gather and use to create the exception. You may even have thought that we should have said
-*/

object B {
//`
fun parseInt(s: String): Either<Exception, Int> = try {
    Right(Integer.parseInt(s))
} catch (exception: Exception) {
    Left(exception)
}
//`
}

/*-
If so, I think you're right. Let's define a helper function to remove some of the boilerplate and refactor.
-*/

//`
inline fun <R> resultOf(f: () -> R): Either<Exception, R> = try {
    Right(f())
} catch (e: Exception) {
    Left(e)
}

fun parseInt(s: String): Either<Exception, Int> = resultOf { Integer.parseInt(s) }

fun BufferedReader.eitherReadLine(): Either<Exception, String> = resultOf {
    this.readLine()
}

fun doubleString(s: String): Either<Exception, Int> = parseInt(s).map { 2 * it }

fun doubleNextLine(reader: BufferedReader): Either<Exception, Int> =
    reader.eitherReadLine().flatMap { doubleString(it) }
//`

/*-
Either<Exception, R> is such a common pattern that you may be tempted give it a special name. Arrow calls it [Try](http://arrow-kt.io/docs/datatypes/try/). There are various other libraries that provide the abstraction as a Result type, including a [proposal for the standard library](https://youtrack.jetbrains.com/issue/KT-18608).

There are a couple of big problems though with making a concrete type that locks in Left to be Exception.

The first problem is, should that be Exception, or Throwable? Arrow says Throwable, but I disagree. If we catch Throwable we are catching Errors, which `indicate serious problems that a reasonable application should not try to catch.` In fact the Scala code on which the Arrow Try is based does not catch Error. The JetBrains Stdlib proposal catches all Throwables, but it is designed to represent the way that an expression terminated, rather than being used for functional error handling.

The second big problem is that functions do not always fail with an exception. Especially if we're just validating parameters in local functions it seems overkill to create an exception, quite an expensive operation, just to in order to represent a failure. So we're probably going to want an Either<String, R> for these cases.

A different way of formalising the use of Either as Result is offered by [Result4K](https://github.com/npryce/result4k). This defines a sealed Result type with with Ok or Err subtypes, making the Right and Left cases clear. It's my favourite of the solutions I've seen so far, but then it is from the home team.

For now then, let's stick with our generic Either type and see what more we can do with it.

If there's one thing that categorises functional programming with monads (no I don't, but Either is) it's delayed gratification. By mapping and flatmapping we don't actually have to deal with errors at low levels - just pass their occurrence back to our callers. As with exceptions though, in the end someone will have to report failure.

Back in [Part 2](failure-is-not-an-option-part-2.html) we used `when` to switch on the type of Either
-*/

fun dummy() {
//`
val result: Either<Exception, Int> = parseInt(readLine() ?: "")
when (result) {
    is Right -> println("Your number was ${result.r}")
    is Left -> println("I couldn't read your number because ${result.l}")
}
//`
}

/*-
The formalisation of this on Either is called `fold`. It takes two functions and returns the result of calling the first if the Either is Left, the second if it is Right.
-*/

//'
inline fun <L, R, T> Either<L, R>.fold(fl: (L) -> T, fr: (R) -> T): T =
    when (this) {
        is Right -> fr(this.r)
        is Left -> fl(this.l)
    }
//'

/*-
This lets us actually do something on the outside of our system
-*/

fun dummy2() {
//`
parseInt(readLine() ?: "").fold(
    fr = { int -> println("Your number was $int") },
    fl = { exception -> println("I couldn't read your number because $exception") }
)
//'
}

/*-
Again you have to squint quite hard to see the similarity between Either.fold and List.fold, but it turns out that functional programmers are so good at squinting that they have given it a special name - catamorphism.

Personally I hate fold. Sure it unwraps the values for you, but putting lambdas inside a function invocation is ugly, and you pretty much always need to name the arguments because you want the success case first, but that doesn't happen naturally when success is Right. I'll often fall back on the raw `when` formulation because it has the braces in the right place and reads naturally. I suppose the nastiness of fold at least encourages the user to only do it as a last resort, which is as it should be.

Before we finish this episode, let's just review what we have.
-*/






