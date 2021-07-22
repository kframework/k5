# Lesson 1.12: Syntactic Lists

The purpose of this lesson is to explain how K provides support for syntactic
repetition through the use of the `List{}` and `NeList{}` constructs,
generally called **syntactic lists**.

## The `List{}` construct

Sometimes, when defining a grammar in K, it is useful to define a syntactic
construct consisting of an arbitrary-length sequence of items. For example,
you might wish to define a function call construct, and need to express a way
of passing arguments to the function. You can in theory simply define these
productions using ordinary constructors, but it can be tricky to get the syntax
exactly right in K without a lot of tedious glue code.

For this reason, K provides a way of specifying that a non-terminal represents
a syntactic list (`lesson-12-a.k`):

```k
module LESSON-12-A-SYNTAX
  imports INT-SYNTAX

  syntax Ints ::= List{Int,","}
endmodule

module LESSON-12-A
  imports LESSON-12-A-SYNTAX
endmodule
```

Note that instead of a sequence of terminals and non-terminals, the right hand
side of the `Ints` production contains the symbol `List` followed by two items
in curly braces. The first item is the non-terminal which is the element type
of the list, and the second item is a terminal representing the separator of
the list. As a special case, lists which are separated only by whitespace can
be specified with a separator of `""`.

This `List{}` construct is roughly equivalent to the following definition
(`lesson-12-b.k`):

```k
module LESSON-12-B-SYNTAX
  imports INT-SYNTAX

  syntax Ints ::= Int "," Ints | ".Ints"
endmodule

module LESSON-12-B
  imports LESSON-12-B-SYNTAX
endmodule
```

As you can see, the `List{}` construct represents a cons-list with an element
at the head and another list at the tail. The empty list is represented by 
a `.` followed by the sort of the list.

However, the `List{}` construct provides several key syntactic conveniences
over the above definition. First of all, when writing a list in a rule, 
explicitly writing the terminator is not always required. For example, consider
the following additional module (`lesson-12-c.k`):

```k
module LESSON-12-C
  imports LESSON-12-A
  imports INT

  syntax Int ::= sum(Ints) [function]
  rule sum(I:Int) => I
  rule sum(I1:Int, I2:Int, Is:Ints) => sum(I1 +Int I2, Is)
endmodule
```

Here we see a function that sums together a non-empty list of integers. Note in
particular the first rule. We do not explicitly mention `.Ints`, but in fact,
the rule in question is equivalent to the following rule:

```
  rule sum(I:Int, .Ints) => I
```

The reason for this is that K will automatically insert a list terminator
anywhere a syntactic list is expected, but an element of that list appears
instead. This works even with lists of more than one element:

```
  rule sum(I1:Int, I2:Int) => I1 +Int I2
```

This rule is redundant, but here we explicitly match a list of exactly two
elements, because the `.Ints` is implicitly added after `I2`.

### Exercise

Write a function `concat` which takes a list of `String` and concatenates them
all together. Do not worry if the function is O(n^2).

## Parsing Syntactic Lists in Programs

An additional syntactic convenience takes place when you want to express a
syntactic list in the input to `krun`. In this case, K will automatically
transform the grammar in `LESSON-12-B-SYNTAX` into the following
(`lesson-12-d.k`):

```k
module LESSON-12-D
  imports INT-SYNTAX

  syntax Ints ::= #NonEmptyInts | #IntsTerminator
  syntax #NonEmptyInts ::= Int "," #NonEmptyInts
                         | Int #IntsTerminator
  syntax #IntsTerminator ::= ""
endmodule
```

This allows you to express the usual comma-separated list of arguments where
an empty list is represented by the empty string, and you don't have to
explicitly terminate the list. Because of this, we can write the syntax
of function calls in C very easily (`lesson-12-e.k`):

```k
module LESSON-12-E
  syntax Id ::= r"[a-zA-Z_][a-zA-Z0-9_]*" [token]
  syntax Exp ::= Id | Exp "(" Exps ")"
  syntax Exps ::= List{Exp,","}
endmodule
```

### Exercise

Write some function call expressions using identifiers in C and verify with 
`kast` that the above grammar captures the intended syntax. Make sure to test
with function calls with zero, one, and two or more arguments.

## The `NeList{}` construct

One limitation of the `List{}` construct is that it is always possible to
write a list of zero elements where a `List{}` is expected. While this is
desirable in a number of cases, it is sometimes not what the grammar expects.

For example, in C, it is not allowable for an `enum` definition to have zero
members. In other words, if we were to write the grammar for enumerations like
so (`lesson-12-f.k`):

```k
module LESSON-12-F
  syntax Id ::= r"[a-zA-Z_][a-zA-Z0-9_]*" [token]
  syntax Exp ::= Id

  syntax EnumSpecifier ::= "enum" Id "{" Ids "}"
  syntax Ids ::= List{Id,","}
endmodule
```

Then we would be syntactically allowed to write `enum X {}`, which instead,
ought to be a syntax error.

For this reason, we introduce the additional `NeList{}` construct. The syntax
is identical to `List{}`, except with `NeList` instead of `List` before the
curly braces. When parsing rules, it behaves identically to the `List{}`
construct. However, when parsing inputs to `krun`, the above grammar, if we
replaced `Ids` with an `NeList{}`, would become equivalent to the following
(`lesson-12-g.k`):

```k
module LESSON-12-G
  syntax Id ::= r"[a-zA-Z_][a-zA-Z0-9_]*" [token]
  syntax Exp ::= Id

  syntax EnumSpecifier ::= "enum" Id "{" Ids "}"
  syntax Ids ::= Id | Id "," Ids
endmodule
```

In other words, only non-empty lists of `Id` would be allowed.

## Exercises

1. Modify the `sum` function in `LESSON-12-C` so that the `Ints` sort is an
`NeList{}`. Verify that calling `sum()` with no arguments is now a syntax
error.

2. Write a modified `sum` function with the `List` construct that can also sum
up an empty list of arguments. In such a case, the sum ought to be 0.

## Next lesson

Once you have completed the above exercises, you can continue to
[Lesson 1.13: Basics of K Rewriting](../13_rewrite_rules/README.md).
