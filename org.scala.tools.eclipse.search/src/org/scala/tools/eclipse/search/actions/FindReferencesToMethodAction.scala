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
      editor <- scalaEditor { error("Active editor wasn't a Scala editor") }
      method <- Helper.getSelectedMethod(editor) { error("You need to have a method selected") }
    } yield {
      val index = SemanticSearchPlugin.index
      val occurrences = index.lookup(method.getElementName())
      val results: Map[String, Seq[Occurrence]] = occurrences.groupBy(_.fileName)
      SemanticSearchPlugin.resultsView.setInput(results)
    }
  }

  private def scalaEditor(otherwise: => Unit): Option[ScalaSourceFileEditor] = {
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