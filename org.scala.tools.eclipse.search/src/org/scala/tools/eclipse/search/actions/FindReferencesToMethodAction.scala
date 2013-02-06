package org.scala.tools.eclipse.search.actions

import scala.reflect.internal.util.OffsetPosition
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.ScalaSourceFileEditor
import scala.tools.eclipse.logging.HasLogger
import scala.tools.nsc.interactive.Response

import org.eclipse.jface.action.IAction
import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.jface.text.ITextSelection
import org.eclipse.jface.viewers.ISelection
import org.eclipse.ui.IWorkbenchWindow
import org.eclipse.ui.IWorkbenchWindowActionDelegate
import org.scala.tools.eclipse.search.EntityFinder
import org.scala.tools.eclipse.search.ErrorHandlingOption
import org.scala.tools.eclipse.search.SemanticSearchPlugin
import org.scala.tools.eclipse.search.SymbolAbstraction
import org.scala.tools.eclipse.search.SymbolAbstraction.Sym
import org.scala.tools.eclipse.search.ui.Helper

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
      symbol    <- symbolAtSelection(editor, selection) ifEmpty error("Couldn't figure out what you've selected")
    } yield {
      val exact = SemanticSearchPlugin.entityFinder.occurrencesOf(symbol)
      val results = exact.groupBy(_.file.file.file.getName())
      SemanticSearchPlugin.resultsView.setInput(results)
    }
  }

  def symbolAtSelection(editor: ScalaSourceFileEditor, selection: ITextSelection): Option[Sym] = {
    editor.getInteractiveCompilationUnit.withSourceFile { (sf, pc) =>
      var r = new Response[pc.Tree]
      pc.askTypeAt(new OffsetPosition(sf, selection.getOffset()), r)
      r.get.fold(
        tree => SymbolAbstraction.fromSymbol(pc)(pc.ask { () => tree.symbol}),
        err => None)
    }(None)
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