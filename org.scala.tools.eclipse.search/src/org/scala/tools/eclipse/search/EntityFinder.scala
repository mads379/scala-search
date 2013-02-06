package org.scala.tools.eclipse.search

import org.scala.tools.eclipse.search.SymbolAbstraction.Sym
import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.nsc.interactive.Response
import scala.reflect.internal.util.OffsetPosition
import scala.tools.eclipse.logging.HasLogger

/**
 * Allows you to search and find entities in Scala projects
 */
object EntityFinder extends HasLogger {

  private val index = SemanticSearchPlugin.index

  /**
   * Find all occurrence of the entity `sym` is representing.
   */
  def occurrencesOf(sym: Sym): Seq[Occurrence] = {
    val occurrences = index.lookup(sym.name)
    exactOccurrences(sym, occurrences)
  }

  private def exactOccurrences(symbol: Sym, occurrences: Seq[Occurrence]): Seq[Occurrence] = {
    occurrences.filter { occurrence =>
      (for {
        other <- getSymbolOfOccurrence(occurrence)
      } yield {
        symbol == other
      }) getOrElse (false)
    }
  }

  private def getSymbolOfOccurrence(occurrence: Occurrence): Option[Sym] = {
    occurrence.file.withSourceFile { (cu, pc) =>
      var r = new Response[pc.Tree]
      val pos = new OffsetPosition(cu, occurrence.offset)
      pc.askTypeAt(pos, r)
      r.get.fold(
        tree => SymbolAbstraction.fromSymbol(pc)(tree.symbol),
        err => {
          logger.debug(err)
          None
        }): Option[Sym]
    }(None)
  }

}