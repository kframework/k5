# Lesson 1.11: Casting Terms

The purpose of this lesson is to explain how to use **cast** expressions in
order to disambiguate terms using sort information. We also explain how the 
variable sort inference algorithm works in K, and how to change the default
behavior by casting variables to a particular sort.

## Casting in K

Sometimes the grammar you write for your rules in K can be a little bit
ambiguous on purpose. While grammars for programming languages may be
unambiguous when considered in their entirety, K allows you to write rules
involving arbitrary **fragments** of that grammar, and those fragments can
sometimes be ambiguous by themselves, or similar enough to other fragments
of the grammar to trigger ambiguity. As a result, in addition to the tools
covered in [Lesson 1.4](../04_disambiguation/README.md), K provides one
additional powerful tool for disambiguation: cast expressions.

K provides three main types of casts: the semantic cast, the strict cast, and
the projection cast. We will cover each of them, and their similarities and
differences, in turn.

### Semantic casts

The most basic, and most common, type of cast in K is called the
**semantic cast**. For every sort `S` declared in a module, K provides the
following (implicit) production for use in sentences:

```
  syntax S ::= S ":S"
```

Note that `S` simply represents the name of the sort. For example, if we 
defined a sort `Exp`, the actual production for that sort would be:

```
  syntax Exp ::= Exp ":Exp"
```

At runtime, this expression will not actually exist; it is merely an annotation
to the compiler describing the sort of the term inside the cast. It is telling
the compiler that the term inside the cast must be of sort `Exp`. For example,
if we had the following grammar:

```k
module LESSON-11-A
  imports INT

  syntax Exp ::= Int | Exp "+" Exp
  syntax Stmt ::= "if" "(" Exp ")" Stmt | "{" "}"
endmodule
```

Then we would be able to write `1:Exp`, or `(1 + 2):Exp`, but not `{}:Exp`.

You can also restrict the sort that a variable in a rule will match by casting
it. For example, consider the following additional module:

```k
module LESSON-11-B
  imports LESSON-11-A
  imports BOOL

  syntax Term ::= Exp | Stmt
  syntax Bool ::= isExpression(Term) [function]

  rule isExpression(E:Exp) => true
  rule isExpression(_) => false [owise]
endmodule
```

Here we have defined a very simple function that decides whether a term is
an expression or a statement. It does this by casting the variable inside the
`isExpression` rule to sort `Exp`. As a result, that variable will only match terms
of sort `Exp`. Thus, `isExpression(1)` will return true, as will `isExpression(1 + 2)`, but
`isExpression({})` will return false.

#### Exercise

Verify this fact for yourself by running `isExpression` on the above examples. Then
write an `isStatement` function, and test that it works as expected.

### Strict casts

On occasion, a semantic cast is not strict enough. It might be that you want
to, for disambiguation purposes, say **exactly** what sort a term is. For
example, consider the following definition:

```k
module LESSON-11-C
  imports INT

  syntax Exp ::= Int | Exp "+" Exp [exp]
  syntax Exp2 ::= Exp | Exp2 "+" Exp2 [exp2]
endmodule
```

This grammar is a little ambiguous and contrived, but it serves to demonstrate
how a semantic cast might be insufficient to disambiguate a term. If we were 
to write the term `(I1:Int + I2:Int):Exp2`, the term would be ambiguous,
because the cast is not sufficiently strict to determine whether you mean
to derive the "+" production tagged `exp`, or the one tagged `exp2`.

In this situation, there is a solution: the **strict cast**. For every sort
`S` in your grammar, K also defines the following production:

```
  syntax S ::= S "::S"
```

This may at first glance seem the same as the previous cast. And indeed,
from the perspective of the grammar and from the perspective of rewriting,
they are in fact identical. However, the second variant has a unique meaning
in the **type system** of K: namely, the term inside the cast cannot be a
**subsort**, i.e., a term of another sort `S2` such that the production
`syntax S ::= S2` exists.

As a result, if we were to write in the above grammar the term
`(I1:Int + I2:Int)::Exp2`, then we would know that the first derivation above
should be chosen, whereas if we want the second derivation, we could write
`(I1:Int + I2:Int)::Exp`.

#### Exercise

Write a function `eval` which takes a term of sort `Exp2` and returns an `Int`.
The function should be capable of evaluating any term of sort `Exp2` to the
integer value which it expresses. You will need to write strict casts in order
to disambiguate these rules. You will not be able to directly write terms of
sort Exp2 directly in a program, so you will have to create new function
symbols that rewrite constant symbols to terms of sort Exp2 and use strict
casts to disambiguate their right-hand side.

### Projection casts

Thus far we have focused entirely on casts which exist solely to inform the 
compiler about the sort of terms. However, sometimes when dealing with grammars
containing subsorts, it can be desirable to reason with the subsort production
itself, which **injects** one sort into another. Remember from above that such
a production looks like `syntax S ::= S2`. This type of production, called a
**subsort production**, can be thought of as a type of inheritance involving
constructors. If we have the above production in our grammar, we say that `S2`
is a subsort of `S`, or that any `S2` is also an `S`. K implicitly maintains a
symbol at runtime which keeps track of where such subsortings occur; this
symbol is called an **injection**.

Sometimes, when one sort is a subsort of another, it can be the case that
a function returns one sort, but you actually want to cast the result of 
calling that function to another sort which is a subsort of the first sort.
This is similar to what happens with inheritance in an object-oriented
language, where you might cast a superclass to a subclass if you know for
sure the object at runtime is in fact an instance of that class.

K provides something similar for subsorts: the **projection cast**.

For each pair of sorts `S` and `S2`, K provides the following production:

```
  syntax S ::= "{" S2 "}" ":>S"
```

What this means is that you take any term of sort `S2` and **cast** it to sort
`S`. If the term of sort S2 consists of an injection containing a term of sort
`S`, then this will return that term. Otherwise, an error occurs and rewriting
fails, returning the projection function which failed to apply. The sort is
not actually checked at compilation time; rather, it a runtime check is
inserted into the code that runs when the rule applies.

For example, here is an example that makes use of projection casts:

```k
module LESSON-11-D
  imports INT

  syntax Exp ::= Int | Bool | Exp "+" Exp | Exp "&&" Exp

  syntax Exp ::= eval(Exp) [function]
  rule eval(I:Int) => I
  rule eval(B:Bool) => B
  rule eval(E1 + E2) => {eval(E1)}:>Int +Int {eval(E2)}:>Int
  rule eval(E1 && E2) => {eval(E1)}:>Bool andBool {eval(E2)}:>Bool
endmodule
```

Here we have defined constructors for a simple expression language over
Booleans and integers, as well as a function `eval` that evaluates these
expressions to a value. Because that value could be an integer or a Boolean,
we need the casts in the last two rules in order to meet the type signature of
`+Int` and `andBool`. Of course, the user can write ill-formed expressions like
`1 && true` or `false + true`, but these will cause errors at runtime, because
the projection cast will fail.

## Exercises

1. Extend the `eval` function in `LESSON-11-D` to include Strings and add a `.`
operator which concatenates them.

2. Modify your solution from lesson 1.9, problem 2 by using an `Exp` sort to
express the integer and Boolean expressions that it supports, in the same style
as `LESSON-11-D`. Then write an `eval` function that evaluates all terms of
sort `Exp` to either a `Bool` or an `Int`.

## Next lesson

Once you have completed the above exercises, you can continue to
[Lesson 1.12: Syntactic Lists](../12_syntactic_lists/README.md).
