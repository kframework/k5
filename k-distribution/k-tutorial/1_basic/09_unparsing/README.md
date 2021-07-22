# Lesson 1.9: Unparsing and the format and color attributes

The purpose of this lesson is to teach the user about how terms are
pretty-printed in K, and how the user can make adjustments to the default
settings for how to print specific terms.

## Parsing, Execution, and Unparsing

When you use `krun` to interpret a program, the tool passes through three major
phases. In the first, parsing, the program itself is parsed using either `kast`
or an ahead-of-time parser generated via Bison, and the resulting AST becomes
the input to the interpreter. In the second phase, execution, K evaluates
functions and (as we will discuss in depth later) performs rewrite steps to
iteratively transform the program state. The third and final phase is called
**unparsing**, because it consists of taking the final state of the application
after the program has been interpreted, and converting it from an AST back into
text that (in theory, anyway) could be parsed back into the same AST that was
the output of the execution phase.

In practice, unparsing is not always precisely reversible. It turns out
(although we are not going to cover exactly why this is here), that
constructing a sound algorithm that takes a grammar and an AST and emits text
that could be parsed via that grammar to the original AST is an
**NP-hard problem**. As a result, in the interests of avoiding exponential time
algorithms when users rarely care about unparsing being completely sound, we
take certain shortcuts that provide a linear-time algorithm that /approximates/
a sound solution to the problem while sacrificing the notion that the result
can be parsed into the exact original term in all cases.

This is a lot of theoretical explanation, but at root, the unparsing process
is fairly simple: it takes a K term that is the output of execution and pretty
prints it according to the syntax defined by the user in their K definition.
This is useful because the original AST is not terribly user-readable, and it
is difficult to visualize the entire term or decipher information about the
final state of the program at a quick glance. Of course, in rare cases, the
pretty-printed configuration loses information of relevance, which is why K
allows you to obtain the original AST on request.

As an example of all of this, consider the following K definition
(`lesson-09-a.k`):

```k
module LESSON-09-A
  imports BOOL

  syntax Exp ::= "(" Exp ")" [bracket]
               | Bool
               > "!" Exp
               > left:
                 Exp "&&" Exp
               | Exp "^" Exp
               | Exp "||" Exp

  syntax Exp ::= id(Exp) [function]
  rule id(E) => E
endmodule
```

This is similar to the grammar we defined in `LESSON-06-C`, with the difference
that the Boolean expressions are now constructors of sort `Exp` and we define a
trivial function over expressions that returns its argument unchanged.

We can now parse a simple program in this definition and use it to unparse some
Boolean expressions. For example (`exp.bool`):

```
id(true&&false&&!true^(false||true))
```

Here is a program that is not particularly legible at first glance, because all
extraneous whitespace has been removed. However, if we run `krun exp.bool`, we
see that the result of the unparser will pretty-print this expression rather
nicely:

```
<k>
  true && false && ! true ^ ( false || true ) ~> .
</k>
```

Notably, not only does K insert whitespace where appropriate, it is also smart
enough to insert parentheses where necessary in order to ensure the correct
parse. For example, without those parentheses, the expression above would parse
equivalent to the following one:

```
(((true && false) && ! true) ^ false) || true
```

Indeed, you can confirm this by passing that exact expression to the `id`
function and evaluating it, then looking at the result of the unparser:

```
<k>
  true && false && ! true ^ false || true ~> .
</k>
```

Here, because the meaning of the AST is the same both with and without
parentheses, K does not insert any parentheses when unparsing.

### Exercise

Modify the grammar of `LESSON-09-A` above so that the binary operators are
right associative. Try unparsing `exp.bool` again, and note how the result is
different. Explain the reason for the difference.

## Custom unparsing of terms

You may have noticed that right now, the unparsing of terms is not terribly
imaginative. All it is doing is taking each child of the term, inserting it
into the non-terminal positions of the production, then printing the production
with a space between each terminal or non-terminal. It is easy to see why this
might not be desirable in some cases. Consider the following K definition
(`lesson-09-b.k`):

```k
module LESSON-09-B
  imports BOOL

  syntax Stmt ::= "{" Stmt "}" | "{" "}"
                > right:
                  Stmt Stmt
                | "if" "(" Bool ")" Stmt
                | "if" "(" Bool ")" Stmt "else" Stmt [avoid]
endmodule
```

This is a statement grammar, simplified to the point of meaninglessness, but
still useful as an objecte lesson in unparsing. Consider the following program
in this grammar (`if.stmt`):

```
if (true) {
  if (true) {}
  if (false) {}
  if (true) {
    if (false) {} else {}
  } else {
    if (false) {}
  }
}
```

This is how that term would be unparsed if it appeared in the output of krun:

```
if ( true ) { if ( true ) { } if ( false ) { } if ( true ) { if ( false ) { } else { } } else { if ( false ) { } } }
```

This is clearly much less legible than we started with! What are we to do?
Well, K provides an attribute, `format`, that can be applied to any production,
which controls how that production gets unparsed. You've seen how it gets
unparsed by default, but via this attribute, the developer has complete control
over how the term is printed. Of course, the user can trivially create ways to
print terms that would not parse back into the same term. Sometimes this is
even desirable. But in most cases, what you are interested in is controlling
the line breaking, indentation, and spacing of the production.

Here is an example of how you might choose to apply the `format` attribute
to improve how the above term is unparsed (`lesson-09-c.k`):

```k
module LESSON-09-C
  imports BOOL

  syntax Stmt ::= "{" Stmt "}" [format(%1%i%n%2%d%n%3)] | "{" "}" [format(%1%2)]
                > right:
                  Stmt Stmt [format(%1%n%2)]
                | "if" "(" Bool ")" Stmt [format(%1 %2%3%4 %5)]
                | "if" "(" Bool ")" Stmt "else" Stmt [avoid, format(%1 %2%3%4 %5 %6 %7)]
endmodule
```

If we compile this new definition and unparse the same term, this is the
result we get:

```
if (true) {
  if (true) {}
  if (false) {}
  if (true) {
    if (false) {} else {}
  } else {
    if (false) {}
  }
}
```

This is the exact same text we started with! By adding the `format` attributes,
we were able to indent the body of code blocks, adjust the spacing of if
statements, and put each statement on a new line.

How exactly was this achieved? Well, each time the unparser reaches a term,
it looks at the `format` attribute of that term. That `format` attribute is a
mix of characters and **format codes**. Format codes begin with the `%`
character. Each character in the `format` attribute other than a format code is
appended verbatim to the output, and each format code is handled according to
its meaning, transformed (possibly recursively) into a string of text, and
spliced into the output at the position the format code appears in the format
string.

Provided for reference is a table with a complete list of all valid format
codes, followed by their meaning:

<table>
<tr><th> Format Code </th><th> Meaning                                          </th></tr>
<tr><td> n           </td><td> Insert '\n' followed by the current indentation
                               level                                            </td></tr>
<tr><td> i           </td><td> Increase the current indentation level by 1      </td></tr>
<tr><td> d           </td><td> Decrease the current indentation level by 1      </td></tr>
<tr><td> c           </td><td> Move to the next color in the list of colors for
                               this production (see next section)               </td></tr>
<tr><td> r           </td><td> Reset color to the default foreground color for
                               the terminal (see next section)                  </td></tr>
<tr><td> an integer  </td><td> Print a terminal or non-terminal from the
                               production. The integer is treated as a 1-based
                               index into the terminals and non-terminals of
                               the production.
<br/>
<br/>                          If the offset refers to a terminal, move to the
                               next color in the list of colors for this
                               production, print the value of that terminal,
                               then reset the color to the default foreground
                               color for the terminal.
<br/>
<br/>                          If the offset refers to a regular expression
                               terminal, it is an error.
<br/>
<br/>                          If the offset refers to a non-terminal, unparse
                               the corresponding child of the current term
                               (starting with the current indentation level)
                               and print the resulting text, then set the
                               current color and indentation level to the color
                               and indentation level following unparsing that
                               term.                                            </td></tr>
<tr><td> other char  </td><td> Print that character verbatim                    </td></tr>
</table>

### Exercise

Change the format attributes for `LESSON-09-C` so that `if.stmt` will unparse
as follows:

```
if (true)
{
  if (true)
  {
  }
  if (false)
  {
  }
  if (true)
  {
    if (false)
    {
    }
    else
    {
    }
  }
  else
  {
    if (false)
    {
    }
  }
}
```

## Output coloring

When the output of unparsing is displayed on a terminal supporting colors, K
is capable of coloring the output, similar to what is possible with a syntax
highlighter. This is achieved via the `color` and `colors` attributes.

Essentially, both the `color` and `colors` attributes are used to construct a
list of colors associated with each production, and then the format attribute
is used to control how those colors are used to unparse the term. At its most
basic level, you can set the `color` attribute to color all the terminals in
the production a certain color, or you can use the `colors` attribute to
specify a comma-separated list of colors for each non-terminal in the
production. At a more advanced level, the `%c` and `%r` format codes control
how the formatter interacts with the list of colors specified by the `colors`
attribute. You can essentially think of the `color` attribute as a way of
specifying that you want all the colors in the list to be the same color.

For example, here is a variant of LESSON-09-A which colors the various boolean
operators:

```k
module LESSON-09-D
  imports BOOL

  syntax Exp ::= "(" Exp ")" [bracket]
               | Bool
               > "!" Exp [color(yellow)]
               > left:
                 Exp "&&" Exp [color(red)]
               | Exp "^" Exp [color(blue)]
               | Exp "||" Exp [color(green)]

  syntax Exp ::= id(Exp) [function]
  rule id(E) => E
endmodule
```

For a complete list of allowed colors, see 
[here](https://github.com/kframework/llvm-backend/blob/master/lib/ast/AST.cpp#L381).

## Exercises

1. Use the color attribute on `LESSON-09-C` to color the keywords `true` and
`false` one color, the keywords `if` and `else` another color, and the operators
`(`, `)`, `{`, and `}` a third color.

2. Use the `format`, `color`, and `colors` attributes to tell the unparser to
style the expression grammar from lesson 1.8, problem 3 according to your own
personal preferences for syntax highlighting and code formatting. You can
view the result of the unparser on a function term without evaluating that
function by means of the command `kast --output pretty <file>`.

## Next lesson

Once you have completed the above exercises, you can continue to
[Lesson 1.10: Strings](../10_strings/README.md).
