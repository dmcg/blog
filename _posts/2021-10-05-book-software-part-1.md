---
title: Writing Software to Write About Writing Software - Part 1
layout: post
tags: [Kotlin, Book]
---

O’Reilly recently published the Kotlin book I wrote with Nat Pryce: [Java to Kotlin: A Refactoring Guidebook](https://java-to-kotlin.dev/). I thought it might be interesting to write about the software we wrote to support our writing.

O’Reilly’s publishing system (Atlas) will accept Word, DocBook or [AsciiDoc](https://asciidoctor.org/) manuscripts. My experience trying to manage simultaneous work in large Word documents has not been great, although perhaps Office 365 is better - I’ve been generally impressed when I have used it. Being developers though, we naturally gravitated to plain-text formats, and our editors steered us towards AsciiDoc rather than the XML-based DocBook. AsciiDoc is a lot like Markdown in that the formatting works reasonably in plain text and can then be rendered into more sophisticated formats like HTML and PDF. Unlike Markdown, it only really has one dialect, but the tooling has migrated from [Python](https://asciidoc-py.github.io/) to [Ruby](https://github.com/asciidoctor) which has led to some versioning issues. We also discovered quite late in the day that Atlas is based on an old and relatively buggy version of AsciiDoctor (the toolchain), but by that time we were committed.

The Atlas publishing system hosts a Git repository, so you can push book source in the supported formats and then click a button in the UI to render a PDF, HTML or EPUB book, which is available to download after a minute or so. For reasons that we couldn't fathom, it doesn't just automatically build every commit. At first Nat built an AsciiDoctor Docker image so that we could render locally, but once we discovered that Atlas was both customised and out of date we settled on using it rather than a local build to be sure that we remained compatible with its idiosyncrasies.

Our book has many, many, many code examples. Obviously we want to make sure that these compile and work as expected, so just typing them into the manuscript wasn't an option. In my [previous attempt](https://leanpub.com/episodes) at writing a book I had chosen to embed the book text into comments in Kotlin source, so I could manage both text and source in IntelliJ. So I had Kotlin source like:

```kotlin
import org.junit.*

/*-
## Title

This is book text - anything in /*- … -*/ blocks is extracted to Markdown.

Code in //` blocks is also extracted.
-*/
class Test {

//`
    @Test fun `2 plus 2 is 4`() {
        assertEquals(4, add(2, 2))
    }
//`
}
/*-
More text here.
 */
```

This would render (to Markdown) as:

```
## Title

This is book text - anything in /*- … -*/ blocks is extracted to Markdown.

Code in //` blocks is also extracted.

```kotlin
@Test fun `2 plus 2 is 4`() {
    assertEquals(4, add(2, 2))
}
.```

More text here.
```

And no, there shouldn't be a dot before the `kotlin` code block closure, but this blog is itself written in Markdown, which appears, at least in its Jekyll incarnation, to be unable to render itself. Apart from that sort of issue, this scheme worked OK, but it was a challenge to manage multiple versions of the same code. I bodged it by using hidden `object` namespaces and occasionally changing packages, but it was clear that the approach wasn’t ideal for a book which would be showing the evolution of code through multiple refactorings.

For our book then, Nat and I decided to separate the manuscript text and example code, pulling the text from the code into the AsciiDoc source with markers. So in our AsciiDoc source we might write:

```asciidoc
A little compiler magic allows Kotlin code to accept a `java.util.List` as a `kotlin.collections.List`:

// begin-insert: src/test/java/collections/ListInteropTest.kt
// end-insert
```

In the best traditions of developer documentation we then wrote code to fetch the contents of `ListInteropTest.kt` and expand it into the AsciiDoc. 

Ordinarily we would take the AsciiDoc source with these markers and write a new file with the inserted code. This new file would then be sent to AsciiDoctor to render. In practice this doesn't work well, because we’d like the book source that we're editing to be as close to the rendered output as reasonable (this is the point of ASCII-based markup after all), and in particular to be able to see the code that we’re writing about there in the editor where we are writing about it. 

So we made the expansion software overwrite the AsciiDoc source document with its expanded version. By making the markers AsciiDoc comments we can leave them in the file when we expand them. They aren't rendered in the final output, but remain in the book source so that we can navigate to the included file, and replace them with the new version when the code is changed. 

The result is that after running the expansion code our AsciiDoc would be:

```asciidoc
A little compiler magic allows Kotlin code to accept a `java.util.List` as a `kotlin.collections.List`:

// begin-insert: src/test/java/collections/ListInteropTest.kt
[source,kotlin]
----
val aList: List<String> = SomeJavaCode.mutableListOfStrings("0", "1")
aList.removeAt(1) // doesn't compile
----
// end-insert
```

This is a trick that has worked well for me in the past. Nat was resistant to the idea at first, and neither of us really liked having a tool that _overwrites_ source files, but in this case it is expedient because it means that the code examples are expanded in the Git version, and we didn't have to work out how to run some sort of pre-build step on Atlas. A handy side-effect is that if we modified the rendering code we could run it and see any behaviour changes as Git diffs to the AsciiDoc source rather than having to compare rendered output - the book source acts as a kind of approval test of its expansion code.

The observant reader might be wondering how `ListInteropTest.kt` compiles if `aList.removeAt(1)` doesn't. Well I’ve been hiding a detail - the book source actually has:

```asciidoc
// begin-insert: src/test/java/collections/ListInteropTest.kt#immutable
// end-insert
```

Here `immutable` is a marker that controls what from the code source is expanded into the book source. With no marker the whole file is inserted (minus import statements and leading and trailing whitespace). With a marker, well, here is the relevant section of the source file:

```kotlin
    @Test
    fun testKotlinCanAcceptJavaListAsImmutableList() {
        /// begin: immutable
        val aList: List<String> = SomeJavaCode.mutableListOfStrings("0", "1")
        /// end: immutable
        /*
        /// begin: immutable
        aList.removeAt(1) // doesn't compile
        /// end: immutable
        */

        assertEquals(listOf("0", "1"), aList)
    }
```

You can see that the code is a test that checks that the behaviour we are documenting is as we say it is. We have only rendered lines between `/// begin` and `/// end` markers with the same tag as the fragment. This gives the desired behaviour in this case, which is that the compiler doesn't see the commented out code when we run our tests on the Kotlin source, and the book reader doesn't see the comments that hide it from the compiler:

```asciidoc
A little compiler magic allows Kotlin code to accept a `java.util.List` as a `kotlin.collections.List`:

// begin-insert: src/test/java/collections/ListInteropTest.kt
[source,kotlin]
----
val aList: List<String> = SomeJavaCode.mutableListOfStrings("0", "1")
aList.removeAt(1) // doesn't compile
----
// end-insert
```

We ended up with a set of `///`-prefixed source markers to control what was rendered. `/// begin` and `/// end` we've seen, `/// mute` and `/// resume` acted in the opposite way and also allowed us to specify some text to show in place of the elided code. So our source Java might be: 

```java
/// begin: excerpt
public class Person {
    private final String firstName;
    private final String lastName;

    public Person(
        String firstName,
        String lastName
    ) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    /// mute: excerpt [... getters, equals, hashCode]
    public String getFirstName() { return firstName; }

    public String getLastName() { return lastName; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Person person = (Person) o;
        return firstName.equals(person.firstName) && lastName.equals(person.lastName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(firstName, lastName, townOrCity, postalCode, countryCode);
    }
    /// resume: excerpt
}
/// end: excerpt
```

but the AsciiDoc would expand thus:

```asciidoc
// begin-insert: src/main/java/builders/Person.java#excerpt
[source,java]
----
public class Person {
    private final String firstName;
    private final String lastName;

    public Person(
        String firstName,
        String lastName,
    ) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    ... getters, equals, hashCode
}
----
// end-insert
```

We flirted with other directives, but with a little creativity these were sufficient to cover all our needs for showing and hiding code. The snipping of code given a source file and marker name, and the expansion of the snipped code into the book source were performed by Kotlin invoked by a Gradle build. Inevitably this involved increasingly complex regular expressions and little territorial wars between Nat and me, but overall it worked well for us. You can see [our code on GitHub](https://github.com/java-to-kotlin/book), but the whole build system is probably an area where you're best writing your own version so that you can customise it to make sense to you.

The tooling as described so far worked well for relatively static code examples, but our book is about refactoring, and in particular long-running sequences of refactorings to migrate from Java to idiomatic Kotlin. We needed to find a way to render multiple versions of the same code, and preferably to show the differences between code at different versions.

We toyed with keeping different versions of the code in different source roots, or different packages, but this turns out to be unsupportable for all the reasons you can probably predict. Instead, we decided to keep the history in Git, and extend the expansion code to fetch a particular version.

The simplest thing that could possibly work is just to include the Git SHA as well as the file path and marker in the AsciiDoc comments:

```asciidoc
// begin-insert: 67b499c6:src/main/java/travelator/itinerary/Route.java#route
// end-insert
```

Now fetch `src/main/java/travelator/itinerary/Route.java` from the commit with SHA `67b499c6`, apply the snippeting with the `route` marker, and expand the results inline in the AsciiDoc.

This turns out, though, to be too simple to _actually_ work. The problem is that writing is an iterative process, and that applies both to the book text and the example code. If we were just documenting the history of a codebase it would be fine, but in practice we found ourselves having to go back to commits and edit them. Sometimes this would be to add snippeting markers or reformatting to fit the book pages, but often we would rework a refactoring from scratch. In any case, editing a commit changes its SHA, and if you rebase later commits, all their SHAs too, so that we would be constantly having to update many markers.

We needed something more stable than SHAs, and settled on commit comments. We prefixed the comments for code versions that we wanted to reference with, for example `encapsulated-collections.0` and then said: 

```asciidoc
// begin-insert: encapsulated-collections.0:src/main/java/travelator/itinerary/Route.java#route
// end-insert
```

Now the expansion code just has to find a commit with a comment prefixed with `encapsulated-collections.0`. I say _just_, but in practice searching a repo for a commit is quite expensive, so in the end Nat wrote a script to apply tags with all the versions to these commits:

```bash
#!/bin/bash
set -eou pipefail

basedir="$(realpath $(dirname "$0"))"
current="$basedir/build/current-tagged"
last="$basedir/build/last-tagged"

cd ../refactoring-to-kotlin-code

function active_branches() {
  git show-ref --heads | grep -v 'refs/heads/attic/' | grep -v 'refs/heads/dummy-master'
}

active_branches | cut -d ' ' -f 1 | shasum > "$current"

if ! diff -q -N "$current" "$last" > /dev/null
then
    tagfmt='*.[0-9]*'

    # delete tags
    git tag -l "$tagfmt" | xargs git tag -d > /dev/null
    
    # Recreate tags
    git log --topo-order --format="%H %S %s" $(active_branches | cut -d ' ' -f 2) | awk '
        $3 ~ /([a-z0-9_\-]+)([.][0-9]+)+/ && $3 != tagname {
            commit = $1
            branch = $2
            tagname = $3

            if (tagname in commits && commits[tagname] != commit) {
                print "ERROR: " tagname " refers to different commits in branches " branch " and " branches[tagname] > "/dev/stderr"
                exit 1
            }

            branches[tagname] = branch
            commits[tagname] = commit

            print tagname, commit
        }
    ' | xargs -n 2 git tag -f
    mv "$current" "$last"
fi
```

No, I've no idea how it works either, but it does, and when it doesn't, it's Nat's problem. My problem was fetching the version of a file with a particular tag (`encapsulated-collections.0` in this case), which is just `git show encapsulated-collections.0:src/main/java/travelator/itinerary/Route.java` in the right directory. The expanding code runs this when it sees the tag prefix and then proceeds as before. If you're interested the latest version of the expanding code is [available on GitHub](https://github.com/java-to-kotlin/book/tree/just-tools/buildSrc/src/main/java/book).

At this point we have a toolchain that allows us to fetch code at a particular version from a Git history and expand it, with some modifications, into the book source. We settled on a strategy of having each chapter in its own `.asciidoc` file: `06-java-to-kotlin-collections.asciidoc`, `07-actions-to-calculations.asciidoc` etc. This meant that we could each work on different chapters without merge issues. Naming the chapters with their number meant that we were constantly renaming them as we reorganised the book, but Git seems to cope well with renaming these days. All the chapters were pulled into a single text with a global AsciiDoc file that used `include` directives:

```ascidoc
:xrefstyle: full
:sectnums:
:sectnumlevels: 2

[role="pagenumrestart"]

include::chapters/01-intro.asciidoc[]

// Foundation
include::chapters/02-java-to-kotlin-projects.asciidoc[]
include::chapters/03-java-to-kotlin-classes.asciidoc[]
include::chapters/04-optional-to-nullable.asciidoc[]
include::chapters/05-beans-to-values.asciidoc[]
include::chapters/06-java-to-kotlin-collections.asciidoc[]
include::chapters/07-actions-to-calculations.asciidoc[]

// Function structure
include::chapters/08-static-methods-to-top-level-functions.asciidoc[]
include::chapters/09-multi-to-single-expression-functions.asciidoc[]
include::chapters/10-functions-to-extension-functions.asciidoc[]
include::chapters/11-methods-to-properties.asciidoc[]
include::chapters/12-functions-to-operators.asciidoc[]

// Pipelines
include::chapters/13-streams-to-sequences.asciidoc[]
include::chapters/14-accumulating-objects-to-transformations.asciidoc[]

// Types
include::chapters/15-encapsulated-collections-to-typealiases.asciidoc[]
include::chapters/16-interfaces-to-functions.asciidoc[]
include::chapters/17-mocks-to-maps.asciidoc[]
include::chapters/18-open-to-sealed-classes.asciidoc[]

// Layers
include::chapters/19-throwing-to-returning.asciidoc[]
include::chapters/20-performing-io-to-passing-data.asciidoc[]
include::chapters/21-exceptions-to-values.asciidoc[]
include::chapters/22-classes-to-functions.asciidoc[]

// Conclusion
include::chapters/23-continuing-the-journey.asciidoc[]

include::bibliography.asciidoc[]
```

Actually, Atlas uses another file, `atlas.json` to specify what it will render and how. In our case this includes other files that make up the whole book but which O'Reilly manage:

```json 
{
  "files": [
    "cover.html",
    "praise.html",
    "titlepage.html",
    "copyright.html",
    "toc.html",
    "text/src/preface.asciidoc",
    "text/src/content.asciidoc",
    "ix.html",
    "author_bio.html",
    "colo.html"
  ]
  //...
}
```

We kept versioned example code in a separate repo to the book text and toolchain - that way we could manage the evolution of the text and examples separately. In the code repo we kept each running example in a separate branch, so that we could modify one example without having to update all the others. For example, there is one example that starts in _Chapter 10: Functions to Extension Functions_, is further refactored in _Chapter 20: Performing I/O to Passing Data_, and again in _Chapter 21: Exceptions to Values_. This code is on a branch named `customer-report`, and has commits with labels like:

```
    exceptions-to-values.7 : flatMap errors
    exceptions-to-values.6 : Collect reasons for failure
    ...
    io-to-data.2 : Show calculation in check
    io-to-data.1 : convert tests to Kotlin
    ...
    extensions.4 : Fix compile
    extensions.3 : Convert HighValueCustomersReport to Kotlin (doesn't compile)
```

Running Nat's script from earlier will give these commits tags:

```markdown
    exceptions-to-values.7
    exceptions-to-values.6
    ...
    io-to-data.2
    io-to-data.1
    ...
    extensions.4
    extensions.3
```

Looking in, for example, `20-performing-io-to-passing-data.asciidoc` we find:

```asciidoc
// begin-insert: io-to-data.2:src/test/java/travelator/marketing/HighValueCustomersReportTests.kt#check
[source,kotlin]
----
private fun check(
    inputLines: List<String>,
    expectedLines: List<String>
) {
    val output = StringWriter()
    val reader = StringReader(inputLines.joinToString("\n"))
    generate(reader, output)
    val outputLines = output.toString().lines()

    assertEquals(expectedLines, outputLines)
}
----
```

As you can imagine, we got pretty good at interactively rebasing the code on these branches, but still it was easy to break code, so that something that had worked no longer did. In the next installment I'll look at how we made sure that all the code that we published in the book worked as expected.