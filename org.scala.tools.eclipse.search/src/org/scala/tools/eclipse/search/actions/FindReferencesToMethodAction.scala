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
import org.scala.tools.eclipse.search.MemoryIndex
import org.scala.tools.eclipse.search.OccurrenceCollector
import org.scala.tools.eclipse.search.Reference

class FindReferencesToMethodAction extends IWorkbenchWindowActionDelegate with HasLogger {

  private var window: IWorkbenchWindow = _

  def dispose() {

  }

  def init(w: IWorkbenchWindow) {
    // TODO: Initialize background indexing thread.
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
                  val index = indexProject(proj)
                  val occurrences = index.lookup(method.getElementName())
                  logger.debug(method.getElementName)
                  logger.debug(occurrences)
                  val references = occurrences.filter( occ => occ.occurrenceKind == Reference)
                  MessageDialog.openInformation( window.getShell(), "TestPDE", "found references: %s".format(references.mkString("\n")))
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

  def indexProject(proj: ScalaProject): MemoryIndex = {
    val index = new MemoryIndex()
    proj.allSourceFiles.foreach {  file =>
      val path = file.getFullPath().toOSString()
      ScalaSourceFile.createFromPath(path).foreach { cu =>
        OccurrenceCollector.findOccurrences(cu).fold(
          fail => logger.debug(fail),
          occurrences => index.addOccurrences(path, occurrences))
      }
    }
    index
  }

  def selectionChanged(action: IAction, selection: ISelection) { }
}