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
import org.scala.tools.eclipse.search.SymbolAbstraction.Sym
import org.scala.tools.eclipse.search.SymbolAbstraction
import org.scala.tools.eclipse.search.EntityFinder
import org.eclipse.jface.text.ITextSelection

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
      val exact = EntityFinder.occurrencesOf(symbol)
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