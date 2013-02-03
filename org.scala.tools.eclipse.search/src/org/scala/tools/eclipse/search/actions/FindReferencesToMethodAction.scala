package org.scala.tools.eclipse.search.actions

import org.eclipse.ui.IWorkbenchWindowActionDelegate
import org.eclipse.jface.action.IAction
import org.eclipse.jface.viewers.ISelection
import org.eclipse.ui.IWorkbenchWindow
import org.eclipse.ui.IFileEditorInput
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.jface.dialogs.MessageDialog
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.ScalaSourceFileEditor
import org.eclipse.jface.text.ITextSelection
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import org.eclipse.jdt.core.IMethod
import org.scala.tools.eclipse.search.indexing.MemoryIndex
import org.scala.tools.eclipse.search.OccurrenceCollector
import org.scala.tools.eclipse.search.Reference
import org.scala.tools.eclipse.search.SemanticSearchPlugin
import org.scala.tools.eclipse.search.Occurrence

class FindReferencesToMethodAction extends IWorkbenchWindowActionDelegate with HasLogger {

  private var window: IWorkbenchWindow = _

  def dispose() {}

  def init(w: IWorkbenchWindow) { 
    window = w
  }

  def getSelectedMethod(editor: ScalaSourceFileEditor): Option[IMethod] = {
    editor.getSelectionProvider().getSelection() match {
      case sel: ITextSelection => {
        val scu: ScalaCompilationUnit = editor.getInteractiveCompilationUnit.asInstanceOf[ScalaCompilationUnit]
        scu.codeSelect(scu, sel.getOffset(), sel.getLength(), null /* TODO: how do I get WorkingCopyOwner*/).headOption.flatMap {
          case x: IMethod => Some(x)
          case _ => None
        }
      }
      case _ => None
    }
  }

  def run(action: IAction) {
    val activeEditor = window.getActivePage.getActiveEditor
    if (activeEditor != null) {
      activeEditor match {
        case scalaEditor: ScalaSourceFileEditor => {
          getSelectedMethod(scalaEditor).foreach { method =>
            scalaEditor.getEditorInput() match {
              case fei: IFileEditorInput => {
                ScalaPlugin.plugin.asScalaProject(fei.getFile().getProject()).foreach { proj =>
                  val index = SemanticSearchPlugin.index
                  val occurrences = index.lookup(method.getElementName())
                  val results: Map[String, Seq[Occurrence]] = occurrences.groupBy(_.fileName) 
                  SemanticSearchPlugin.resultsView.setInput(results)
                }
              }
              case _ => {}
            }
          }
        }
        case _ => {
          MessageDialog.openInformation( window.getShell(), "TestPDE", "Wasn't a Scala editor")
        }
      }
    }
  }

  def selectionChanged(action: IAction, selection: ISelection) { }
}