package org.scala.tools.eclipse.search.jobs

import scala.tools.eclipse.logging.HasLogger
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Path
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.search.ui.NewSearchUI
import org.eclipse.ui.IWorkbenchPage
import org.eclipse.ui.ide.IDE
import org.eclipse.ui.part.FileEditorInput
import org.scala.tools.eclipse.search.Occurrence
import org.scala.tools.eclipse.search.SemanticSearchPlugin
import org.scala.tools.eclipse.search.ui.Helper
import org.scala.tools.eclipse.search.ui.MarkerHelper
import scala.tools.eclipse.ScalaSourceFileEditor
import scala.tools.eclipse.util.EclipseFile

/**
 * Needs to be created on the UI thread.
 */
class OpenOccurrenceJob(occurrence: Occurrence, page: IWorkbenchPage) extends Job("Open Occurrence Job") with HasLogger {

  //
  // We have all of this in the constructor because it's accessing objects that
  // can only be accessed on the UI thread.
  //
  
  // val file: IFile = 
  
  val file: IFile = occurrence.file.file.asInstanceOf[EclipseFile].underlying
  val input = new FileEditorInput(file)
  val desc = IDE.getEditorDescriptor(file.getName())
  val part = IDE.openEditor(page, input, desc.getId())
  val editor = Helper.getTextEditor(input, part)
  val document = editor.getDocumentProvider().getDocument(editor.getEditorInput());

  def run(monitor: IProgressMonitor): IStatus = {
    file.deleteMarkers(NewSearchUI.SEARCH_MARKER, true, IResource.DEPTH_INFINITE)
    val mark = MarkerHelper.createMarker(occurrence, document, file, editor)
    MarkerHelper.goToMarker(mark, editor)
    Status.OK_STATUS
  }

}