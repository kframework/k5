# Lesson 1.14: Defining Evaluation Order

The purpose of this lesson is to explain how to use the `heat` and `cool`
attributes, `context` and `context alias` sentences, and the `strict` and 
`seqstrict` attributes to more compactly express heating and cooling in K,
and to express more advanced evaluation strategies in K.

## The `heat` and `cool` attributes

Thus far, we have been using rule priority and casts to express when to heat
an expression and when to cool it. For example, the rules for heating have
lower priority, so they do not apply if the term could be evaluated instead,
and the rules for heating are expressly written only to apply if the argument
of the expression is a value.

However, K has built-in support for deciding when to heat and when to cool.
This support comes in the form of the rule attributes `heat` and `cool` as
well as the specially named function `isKResult`.

Consider the following definition, which is equivalent to `LESSON-13-C`
(`lesson-14-a.k`):

```k
module LESSON-14-A-SYNTAX
  imports UNSIGNED-INT-SYNTAX
  imports BOOL-SYNTAX

  syntax Exp ::= Int
               | Bool
               > left: Exp "+" Exp
               > left: Exp "&&" Exp
endmodule

module LESSON-14-A
  imports LESSON-14-A-SYNTAX
  imports INT
  imports BOOL

  rule <k> I1:Int + I2:Int => I1 +Int I2 ...</k>
  rule <k> B1:Bool && B2:Bool => B1 andBool B2 ...</k>

  syntax KItem ::= freezer1(Exp) | freezer2(Exp)
                 | freezer3(Exp) | freezer4(Exp)

  rule <k> E:Exp + HOLE:Exp => HOLE ~> freezer1(E) ...</k>
    requires isKResult(E) [heat]
  rule <k> HOLE:Exp + E:Exp => HOLE ~> freezer2(E) ...</k> [heat]
  rule <k> E:Exp && HOLE:Exp => HOLE ~> freezer3(E) ...</k>
    requires isKResult(E) [heat]
  rule <k> HOLE:Exp && E:Exp => HOLE ~> freezer4(E) ...</k> [heat]

  rule <k> HOLE:Exp ~> freezer1(E) => E + HOLE ...</k> [cool]
  rule <k> HOLE:Exp ~> freezer2(E) => HOLE + E ...</k> [cool]
  rule <k> HOLE:Exp ~> freezer3(E) => E && HOLE ...</k> [cool]
  rule <k> HOLE:Exp ~> freezer4(E) => HOLE && E ...</k> [cool]

  syntax Bool ::= isKResult(K) [function, symbol]
  rule isKResult(_:Int) => true
  rule isKResult(_:Bool) => true
  rule isKResult(_) => false [owise]
endmodule
```

We have introduced three major changes to this definition. First, we have
removed the `Val` sort. We replace it instead with a function `isKResult`.
The function in question must have the same signature and attributes as seen in
this example. It ought to return true whenever a term should not be heated
(because it is a value) and false when it should be heated (because it is not
a value). We thus also insert `isKResult` calls in the side condition of two
of the heating rules, where the `Val` sort was previously used.

Second, we have removed the rule priorities on the heating rules and the use of
the `Val` sort on the cooling rules, and replaced them with the `heat` and
`cool` attributes. These attributes instruct the compiler that these rules are
heating and cooling rules, and thus should implicitly apply only when certain
terms on the LHS either are or are not a `KResult` (i.e., `isKResult` returns
`true` versus `false`).

Third, we have renamed some of the variables in the heating and cooling rules
to the special variable `HOLE`. Syntactically, `HOLE` is just a special name
for a variable, but it is treated specially by the compiler. By naming a
variable `HOLE`, we have informed the compiler which term is being heated
or cooled. The compiler will automatically insert the side condition
`requires isKResult(HOLE)` to cooling rules and the side condition
`requires notBool isKResult(HOLE)` to heating rules.

### Exercise

Modify `LESSON-14-A` to add rules to evaluate integer subtraction.

## Simplifying further with Contexts

The above example is still rather cumbersome to write. We must explicitly write
both the heating and the cooling rule separately, even though they are
essentially inverses of one another. It would be nice to instead simply
indicate which terms should be heated and cooled, and what part of them to
operate on.

To do this, K introduces a new type of sentence, the **context**. Contexts
begin with the `context` keyword instead of the `rule` keyword, and usually
do not contain a rewrite operator.

Consider the following definition which is equivalent to `LESSON-14-A`
(`lesson-14-b.k`):

```k
module LESSON-14-B-SYNTAX
  imports UNSIGNED-INT-SYNTAX
  imports BOOL-SYNTAX

  syntax Exp ::= Int
               | Bool
               > left: Exp "+" Exp
               > left: Exp "&&" Exp
endmodule

module LESSON-14-B
  imports LESSON-14-B-SYNTAX
  imports INT
  imports BOOL

  rule <k> I1:Int + I2:Int => I1 +Int I2 ...</k>
  rule <k> B1:Bool && B2:Bool => B1 andBool B2 ...</k>

  context <k> E:Exp + HOLE:Exp ...</k>
    requires isKResult(E)
  context <k> HOLE:Exp + _:Exp ...</k>
  context <k> E:Exp && HOLE:Exp ...</k>
    requires isKResult(E)
  context <k> HOLE:Exp && _:Exp ...</k>

  syntax Bool ::= isKResult(K) [function, symbol]
  rule isKResult(_:Int) => true
  rule isKResult(_:Bool) => true
  rule isKResult(_) => false [owise]
endmodule
```

In this example, the `heat` and `cool` rules have been removed entirely, as
have been the productions defining the freezers. Don't worry, they still exist
under the hood; the compiler is just generating them automatically. For each
context sentence like above, the compiler generates a `#freezer` production,
a `heat` rule, and a `cool` rule. The generated form is equivalent to the
rules we wrote manually in `LESSON-14-A`. However, we are now starting to
considerably simplify the definition. Instead of 3 sentences, we just have one.

## `context alias` sentences and the `strict` and `seqstrict` attributes

Notice that the contexts we included in `LESSON-14-B` still seem rather
similar in form. For each expression we want to evaluate, we are declaring
one context for each operand of that expression, and they are each rather
similar to one another. We would like to be able to simplify further by
simply annotating each expression production with information about how
it is to be evaluated instead. We can do this with the `seqstrict` attribute.

Consider the following definition, once again equivalent to those above
(`lesson-14-c.k`):

```{.k .alias}
module LESSON-14-C-SYNTAX
  imports UNSIGNED-INT-SYNTAX
  imports BOOL-SYNTAX

  syntax Exp ::= Int
               | Bool
               > left: Exp "+" Exp [seqstrict(exp; 1, 2)]
               > left: Exp "&&" Exp [seqstrict(exp; 1, 2)]
endmodule

module LESSON-14-C
  imports LESSON-14-C-SYNTAX
  imports INT
  imports BOOL

  rule <k> I1:Int + I2:Int => I1 +Int I2 ...</k>
  rule <k> B1:Bool && B2:Bool => B1 andBool B2 ...</k>

  context alias [exp]: <k> HERE ...</k>

  syntax Bool ::= isKResult(K) [function, symbol]
  rule isKResult(_:Int) => true
  rule isKResult(_:Bool) => true
  rule isKResult(_) => false [owise]
endmodule
```

This definition has two important changes from the one above. The first is
that the individual `context` sentences have been removed and have been
replaced with a single `context alias` sentence. You may notice that this
sentence begins with an identifier in square brackets followed by a colon. This
syntax is a way of naming individual sentences in K for reference by the tool
or by other sentences. The context alias sentence also has a special variable
`HERE`.

The second is that the productions in `LESSON-14-C-SYNTAX` have been given a
`seqstrict` attribute. The value of this attribute has two parts. The first
is the name of a context alias sentence. The second is a comma-separated list
of integers. Each integer represents an index of a non-terminal in the
production, counting from 1. For each integer present, the compiler implicitly
generates a new `context` sentence according to the following rules:

1. The compiler starts by looking for the `context alias` sentence named. If
there is more than one, then one `context` sentence is created per
`context alias` sentence with that name.
2. For each `context` created, the variable `HERE` in the context alias is
substituted with an instance of the production the `seqstrict` attribute is
attached to. Each child of that production is a variable. The non-terminal
indicated by the integer offset of the `seqstrict` attribute is given the name
`HOLE`.
3. For each integer offset prior in the list to the one currently being
processed, the predicate `isKResult(E)` is conjuncted together and included
as a side condition, where `E` is the child of the production term with that
offset, starting from 1. For example, if the attribute lists `1, 2`, then
the rule generated for the `2` will include `isKResult(E1)` where `E1` is the
first child of the production.

As you can see if you work through the process, the above code will ultimately
generate the same contexts present in `LESSON-14-B`.

Finally, note that there are a few minor syntactic conveniences provided by the
`seqstrict` attribute. First, if you want your `context alias` sentence to look
exactly like `<k> HERE ...</k>`, you can omit both the `context alias` sentence
from the definition, and the name from the `seqstrict` attribute.

Second, if the numbered list of offsets contains every non-terminal in the
production, it can be omitted from the attribute value.

Thus, we can finally produce the idiomatic K definition for this example
(`lesson-14-d.k`):

```k
module LESSON-14-D-SYNTAX
  imports UNSIGNED-INT-SYNTAX
  imports BOOL-SYNTAX

  syntax Exp ::= Int
               | Bool
               > left: Exp "+" Exp [seqstrict]
               > left: Exp "&&" Exp [seqstrict]
endmodule

module LESSON-14-D
  imports LESSON-14-D-SYNTAX
  imports INT
  imports BOOL

  rule <k> I1:Int + I2:Int => I1 +Int I2 ...</k>
  rule <k> B1:Bool && B2:Bool => B1 andBool B2 ...</k>

  syntax Bool ::= isKResult(K) [function, symbol]
  rule isKResult(_:Int) => true
  rule isKResult(_:Bool) => true
  rule isKResult(_) => false [owise]
endmodule
```

### Exercise

Modify `LESSON-14-D` to add a production and rule to evaluate integer
subtraction.

## Nondeterministic evaluation order with the `strict` attribute

Thus far, we have focused entirely on deterministic evaluation order. However,
not all languages are deterministic in the order they evaluate expressions.
For example, in C, the expression `a() + b() + c()` is guaranteed to parse
to `(a() + b()) + c()`, but it is not guaranteed that `a` will be called before
`b` before `c`. In fact, this evaluation order is non-deterministic.

We can express non-deterministic evaluation orders with the `strict` attribute.
Its behavior is identical to the `seqstrict` attribute, except that step 3 in
the above list (with the side condition automatically added) does not take
place. In other words, if we wrote `syntax Exp ::= Exp "+" Exp [strict]`
instead of `syntax Exp ::= Exp "+" Exp [seqstrict]`, it would generate the
following two contexts instead of the ones found in `LESSON-14-B`:

```
  context <k> _:Exp + HOLE:Exp ...</k>
  context <k> HOLE:Exp + _:Exp ...</k>
```

As you can see, these contexts will generate heating rules that can both
apply to the same term. As a result, the choice of which heating rule
applies first is non-deterministic, and as we saw in Lesson 1.13, we can
 get all possible behaviors by passing `--search` to krun.

## Exercises

1. Add integer division to `LESSON-14-C`. Make division and addition `strict`
instead of `seqstrict`, and write a rule evaluating integer division with a
side condition that the denominator is non-zero. Run `krun --search` on the
program `1 / 0 + 2 / 1` and observe all possible outputs of the program. How
many are there total, and why?

2. Modify your solution from lesson 1.11 problem 2 to remove the `eval`
function and instead evaluate expressions from left to right using the
`seqstrict` attribute.

## Next lesson

Once you have completed the above exercises, you can continue to
[Lesson 1.15: Configuration Declarations and Cell Nesting](../15_configurations/README.md).
