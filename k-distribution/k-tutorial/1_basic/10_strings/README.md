# Lesson 1.10: Strings

The purpose of this lesson is to explain how to use the `String` sort in K to
represent **sequences of characters**, and explain where to find additional
information about builtin functions over strings.

## The `String` Sort

In addition to the `Int` and `Bool` sorts covered in
[Lesson 1.6](../06_ints_and_bools/README.md), K provides, among others, the
`String` sort to represent sequences of characters. You can import this
functionality via the `STRING-SYNTAX` module, which contains the syntax of
string literals in K, and the `STRING` module, which contains all the functions
that operate over the `String` type.

Strings in K are double-quoted. The following list of escape sequences is
supported:

| Escape Sequence | Meaning                                                   |
| --------------- | --------------------------------------------------------- |
| `\"`            | The literal character "                                   |
| `\\`            | The literal character \                                   |
| `\n`            | The newline character (ASCII code 0x0a)                   |
| `\r`            | The carriage return character (ASCII code 0x0d)           |
| `\t`            | The tab character (ASCII code 0x09)                       |
| `\f`            | The form feed character (ASCII code 0x0c)                 |
| `\x00`          | \x followed by 2 hexadecimal digits indicates a code point between 0x00 and 0xFF |
| `\u0000`        | \u followed by 4 hexadecimal digits indicates a code point between 0x0000 and 0xFFFF |
| `\U00000000`    | \U followed by 8 hexadecimal digits indicates a code point between 0x000000 and 0x10FFFF |

Please note that as of the current moment, K's unicode support is not fully
complete, so you may run into errors using code points greater than 0xff.

As an example, you can construct a string literal containing the following
block of text:

```
This is an example block of text.
Here is a quotation: "Hello world."
	This line is indented.
ÁÉÍÓÚ
```

Like so:

```
"This is an example block of text.\nHere is a quotation: \"Hello world.\"\n\tThis line is indented.\n\xc1\xc9\xcd\xd3\xda\n"
```

## Basic String Functions

The full list of functions provided for the `String` sort can be found in
[domains.md](../../../include/kframework/builtin/domains.md), but here we
describe a few of the more basic ones.

### String concatenation

The concatenation operator for strings is `+String`. For example, consider
the following K rule that constructs a string from component parts
(`lesson-10.k`):

```k
module LESSON-10
  imports STRING

  syntax String ::= msg(String) [function]
  rule msg(S) => "The string you provided: " +String S +String "\nHave a nice day!"
endmodule
```

Note that this operator is `O(N)`, so repeated concatenations are inefficient.
For information about efficient string concatenation, refer to
[Lesson 2.14](../../2_intermediate/14_string_buffers_and_bytes/README.md).

### String length

The function to return the length of a string is `lengthString`. For example,
`lengthString("foo")` will return 3, and `lengthString("")` will return 0.
The return value is the length of the string in **code points**.

### Substring computation

The function to compute the substring of a string is `substrString`. It
takes two string indices, starting from 0, and returns the substring within the
range [start..end). It is only defined if `end >= start`, `start >= 0`, and
`end <= length of string`. Here, for example, we return the first 5 characters
of a string:

```
substrString(S, 0, 5)
```

Here we return all but the first 3 characters:

```
substrString(S, 3, lengthString(S))
```

## Exercises

1. Write a function that takes a paragraph of text (i.e., a sequence of
sentences, each ending in a period), and constructs a new (nonsense) sentence
composed of the first word of each sentence, followed by a period. Do not
worry about capitalization or periods within the sentence which do not end the
sentence (e.g. "Dr."). For more information about the functions over strings
required to implement such a function, refer to `domains.md`.

## Next lesson

Once you have completed the above exercises, you can continue to
[Lesson 1.11: Casting Terms](../11_casts/README.md).
