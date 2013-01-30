package org.scala.tools.eclipse.search

import org.eclipse.ui.IWorkbenchWindowActionDelegate
import org.eclipse.jface.action.IAction
import org.eclipse.jface.viewers.ISelection
import org.eclipse.ui.IWorkbenchWindow
import org.eclipse.ui.IFileEditorInput
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.core.resources.IFile
import org.eclipse.jface.dialogs.MessageDialog
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.javaelements.ScalaSourceFile

class SampleAction extends IWorkbenchWindowActionDelegate with HasLogger {

  private var window: IWorkbenchWindow = _

  def dispose() {

  }

  def init(w: IWorkbenchWindow) {
    window = w
  }

  def run(action: IAction) {
    val activeEditor = window.getActivePage.getActiveEditor
    if (activeEditor != null) {
      activeEditor.getEditorInput match {
        case fei: IFileEditorInput =>
          display(fei.getFile)
      }
    }
  }

  def selectionChanged(action: IAction, selection: ISelection) {

  }

  def display(file: IFile) {

    val path = file.getFullPath().toOSString()
    ScalaSourceFile.createFromPath(path).foreach { cu =>
      logger.debug("Searching")
      logger.debug(OccurrenceCollector.findOccurrences(cu))
    } 
    
    

    MessageDialog.openInformation(
      window.getShell(),
      "TestPDE",
      getProjectName(file)
        .map("The Scala project for the current editor is %s".format(_))
        .getOrElse("The current editor is not part of a Scala project"))
  }

  def getProjectName(file: IFile): Option[String] = {
    ScalaPlugin.plugin.asScalaProject(file.getProject).map(_.underlying.getName)
  }

}