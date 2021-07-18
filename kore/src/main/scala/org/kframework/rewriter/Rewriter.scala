package org.kframework.rewriter

import java.util.Optional

import org.kframework.definition.{Module, Rule}
import org.kframework.{RewriterResult, kore}

trait RewriterConstructor extends (Module => Rewriter)

trait Rewriter {
  //  def normalize(k: K): K
  //  def substitute(k: K, s: KVariable => K): K

  //  def step(k: K): K

  /**
   * (disregard this javadoc comment for now)
   * Takes one rewriting step.
   * - for regular execution, it returns the next K or False (i.e. an empty Or)
   * - for symbolic execution, it can return any formula with symbolic constraints
   * - for search, it returns an Or with multiple ground terms as children
   */
  def execute(k: kore.K, depth: Optional[Integer]): RewriterResult


  def `match`(k: kore.K, rule: Rule): kore.K


  /**
   * Execute a search of the Transition System.
   * @param initialConfiguration The configuration to begin searching from.
   * @param depth No. of transitions to consider before termination (Depth of Tree to traverse). Empty represents unbounded.
   * @param bound No. of states to consider as final results. Empty represents unbounded.
   * @param pattern The rule (pattern + side condition) that we're trying to find a substitution for.
   * @return A list of substitutions, denoting all the configurations matching the given rule.
   */
  def search(initialConfiguration: kore.K, depth: Optional[Integer], bound: Optional[Integer], pattern: Rule, searchType: SearchType): kore.K

  def executeAndMatch(k: kore.K, depth: Optional[Integer], rule: Rule): Tuple2[RewriterResult, kore.K]

  def prove(rules: Module, boundaryPattern: Rule, reuseDef:java.lang.Boolean): RewriterResult

  def bmc(rules: Module): RewriterResult

  def equivalence(firstDef: Rewriter, secondDef: Rewriter, firstSpec: Module, secondSpec: Module): Boolean
}
