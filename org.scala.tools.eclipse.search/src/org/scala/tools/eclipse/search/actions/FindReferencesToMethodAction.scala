package org.scala.tools.eclipse.search.actions

import scala.tools.eclipse.ScalaSourceFileEditor
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.jface.action.IAction
import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.jface.viewers.ISelection
import org.eclipse.ui.IWorkbenchWindow
import org.eclipse.ui.IWorkbenchWindowActionDelegate
import org.scala.tools.eclipse.search.Occurrence
import org.scala.tools.eclipse.search.SemanticSearchPlugin
import org.scala.tools.eclipse.search.ui.Helper
import org.scala.tools.eclipse.search.ui.SearchView
import scala.tools.eclipse.ScalaProject
import scala.tools.nsc.interactive.Response
import scala.reflect.api.Position
import scala.reflect.internal.util.OffsetPosition
import scala.reflect.internal.util.SourceFile
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path
import org.eclipse.core.resources.IFile
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.reflect.internal.util.SourceFile
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.ScalaPresentationCompiler
import org.scala.tools.eclipse.search.{ ErrorHandlingOption }

class FindReferencesToMethodAction
  extends IWorkbenchWindowActionDelegate
     with HasLogger {

  private var window: IWorkbenchWindow = _

  def dispose() {}

  def init(w: IWorkbenchWindow) {
    window = w
  }

  def run(action: IAction) {
    for {
      editor    <- scalaEditor ifEmpty error("Active editor wasn't a Scala editor")
      selection <- Helper.getSelection(editor) ifEmpty error("You need to have a selection")
    } yield {
      editor.getInteractiveCompilationUnit.doWithSourceFile { (sf, pc) =>
        var r = new Response[pc.Tree]
        pc.askTypeAt(new OffsetPosition(sf, selection.getOffset()), r)
        r.get.fold(
            tree => {
              val symbol = pc.ask { () => tree.symbol }
              val index = SemanticSearchPlugin.index
              val occurrences = index.lookup(symbol.nameString)
              val exact = exactOccurrences(pc)(tree.symbol, occurrences)
              val results: Map[String, Seq[Occurrence]] = exact.groupBy(_.file.file.file.getName())
              SemanticSearchPlugin.resultsView.setInput(results)
            },
            err => {
              error(err.getStackTraceString)
            })
      }
    }
  }

  private def exactOccurrences(pc: ScalaPresentationCompiler)
                      (symbol: pc.Symbol, occurrences: Seq[Occurrence]): Seq[Occurrence] = {
    occurrences.filter { occurrence =>
      val other = getSymbolOfOccurrence(pc)(occurrence)
      val eq = symbol == other 
      logger.debug("comparing %s with %s , was %s".format(symbol.nameString, other.nameString, eq))
      eq
    }
  }

  private def getSymbolOfOccurrence(pc: ScalaPresentationCompiler)(occurrence: Occurrence): pc.Symbol = {
    occurrence.file.withSourceFile{ (cu, _) =>
      var r = new Response[pc.Tree]
      val pos = new OffsetPosition(cu, occurrence.offset)
      pc.askTypeAt(pos, r)
      r.get.fold(
          tree => tree.symbol,
          err => {
            logger.debug(err)
            pc.NoSymbol
          }) : pc.Symbol
    }(pc.NoSymbol)
  }

  def getProjectOfEditor(editor: ScalaSourceFileEditor): ScalaProject = {
    editor.getInteractiveCompilationUnit.scalaProject
  }

  private def scalaEditor: Option[ScalaSourceFileEditor] = {
    for {
      page <- Option(window.getActivePage)
      editor <- Option(page.getActiveEditor())
      scalaEditor <- if (editor.isInstanceOf[ScalaSourceFileEditor]) Some(editor) else None
    } yield scalaEditor.asInstanceOf[ScalaSourceFileEditor]
  }

  private def error(str: String): Unit = {
    MessageDialog.openInformation( window.getShell(), "TestPDE", str)
  }

  def selectionChanged(action: IAction, selection: ISelection) { }
}