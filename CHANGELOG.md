K Framework 5.2.0
=================

**TODO**: Current as of 61eec9a398

Feature Additions
-----------------

-   `kompile` will now attempt to simplify constant expressions which appear
    in the RHS of rules.

-   The LLVM backend now supports the non-deterministic `search` feature.
    This must be enabled at `kompile` time with `--enable-search --backend llvm`
    and only supports the `--search|--search-final` options.

Commits: #1896 - 42dbd6af22, #1946 - 2403a5c09a, #1958 - c4c5582232,
         #1978 - 3eebe42d24

Feature Removals
----------------

-   The sentence type `imports syntax` has been removed. It was only used in one
    place in the tutorial, which was easy to resolve. This allows simplifying
    several code-paths in `kompile`.

Commits: #1927 - 3dfdd085d0

Kore Encoding and Builtins
--------------------------

-   The K formula calculating Array extension has been optimized in
    `domains.md`.

-   All modules which contain `configuration` declarations will automatically
    import the `MAP` module now, instead of only the main module.

-   The LLVM backend now also sees `simplification` rules, and is responsible
    for ignoring them.

-   The new Haskell backend hooks for `encodeBytes` and `decodeBytes` are
    are exposed in `domains.md`.

Commits: #1952 - 2b5da87ca7, #1965 - b3ee477caa, #1983 - e6161cb9d9,
         #2002 - c4151b2d75

Documentation
-------------

-   The option `--help-experimental` or `-X` is removed from all tools, and
    all the `--help` option lists are flattened.

-   Documentation for `symbol`, `klabel`, `simplification`, and `smt-lemma`
    attributes is updated in `pending-documentation.md`. Documentation for
    the `#Layout` sort is updated in `kast.md`.

-   Lessons 2-12 of the new K tutorial have been written.

-   Many broken links on the website are fixed.

Commits: #1929 - cf8d4265a8, #1930 - 35a7b6d37d, #1995 - 1c46185602,
         #1997 - 561e0f6e8c, #2023 - 61eec9a398, #2018 - c017a74a88,
         #2011 - 2f589a36ba, #2005 - ef39d1f0fb, #1994 - 58ea05e4ee,
         #1993 - 732519f55d, #1992 - 7a4cb62fa8, #1973 - 58cc924ba8,
         #1847 - 398356f4d2, #2006 - 6e1da47009, #2000 - 85d5664732,
         #1970 - a7f37e4a45, #2013 - 0186735835

Parsing Performance
-------------------

-   Definition parsing performance is significantly improved by being smarter
    with the existing caches and optimizing some specific code hotspots.

Commits: #2012 - c747292f23, #2001 - 787add05a4, #1996 - e655bcc108,
         #1962 - b9581a3bd4, #1944 - 9f8a111e34

Misc
----

-   Modules marked as `not-lr1` are automatically excluded from the generated
    Bison parser, instead of causing an error.

-   `krun` respects the `--no-exc-wrap` option now.

-   Formatting of several error messages is improved, especially indentation.

-   File symlink handling in `requires` statements is improved.

-   `kast` tool no longer silently ignores the `--output-file` parameter.

Commits: #2010 - ff680bd9d8, #1999 - c294c372b9, #1987 - 7a482220be,
         #1991 - f244e528a1, #1984 - 04cf28cea4, #1981 - d939ea3a4e,
         #1982 - f4526b0322, #1931 - 31a97a54b8

Packaging/Deployment
--------------------

-   The automatic deployment release trigger is fixed.

-   The Nix packages are built with with LTO turned on. Bison is included
    in the Nix package dependencies. `kserver` now works on Nix.

-   The platform independent K binary, which really would only realistically
    work with Ubuntu Bionic (18.04), has been removed as an install option.

-   The Arch linux Dockerfiles have been updated for new Arch docker image
    naming scheme.

Commits: #1935 - 4c363a4ceb, #2004 - 800cd8cf2c, #1884 - 312e54d183,
         #1964 - cf17ed5497, #1960 - 2225e0ef3a, #1948 - cb7dadd046,
         #1975 - 506d2019e1, #1951 - 04c747e243, #2014 - 0ebf654633,
         #2014 - bae0b77226

Dependency Updates
------------------

-   Haskell backend is updated to version 67d4d2369.

-   LLVM backend is updated to version 789d437.

-   K Web Theme is updated to version 4d2c931.

-   JavaCC is updated to version 4.1.4.

-   commons-io Java library is bumped to version 2.7.

Commits: #2009 - c206850a7e, #2008 - 68a1a25b64, #2003 - 94148f87ee,
         #1985 - b93192ee41, #1980 - a64e023e39, #1976 - f15c91057d,
         #1971 - 29e1e20d1f, #1939 - 6dff3c2663, #1932 - c7c2dac7e0,
         #2021 - cbce3c86b1, #1959 - 6ac93bc91e, #1954 - 925136722e,
         #1974 - 051c46a3e1, #1942 - de29b8fcff

K Framework 5.1.0
=================

Features
--------

-   Use the `claim` keyword instead of `rule` for proof obligations.
-   Bison parser is full-featured and supports external ahead-of-time `macro` and `macro-rec` expansion.
-   The new `krun` pipeline is written in `bash`, and can often skip running the Java frontend altogether.
    In particular, if `kompile` is called using the Bison parser (`--gen-bison-parser` or `--gen-glr-bison-parser`), you get this benefit.
-   K semantic version numbers are re-introduced, and can be expected to increment (rather than relying on git commit IDs).

K Framework 5.0.0
=================

Major changes since version 3.6, not all are documented here.
In particular:

-   No longer user Maude backend for K.
-   OCaml backend was developed, but now is being deprecated in favor of LLVM backend for concrete execution.
-   Java backend was developed, but now is being deprecated in favor of Haskell backend for symbolic execution.
