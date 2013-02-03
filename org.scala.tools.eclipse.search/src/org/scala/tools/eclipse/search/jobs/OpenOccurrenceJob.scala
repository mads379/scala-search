package org.scala.tools.eclipse.search.jobs

import org.scala.tools.eclipse.search.Occurrence
import org.eclipse.ui.IWorkbenchPage
import org.eclipse.core.resources.IFile
import org.scala.tools.eclipse.search.SemanticSearchPlugin
import org.eclipse.ui.part.FileEditorInput
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.ui.ide.IDE
import org.eclipse.core.runtime.IStatus
import org.scala.tools.eclipse.search.ui.MarkerHelper
import org.eclipse.core.runtime.Status
import org.scala.tools.eclipse.search.ui.Helper
import org.eclipse.core.runtime.jobs.Job
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.core.runtime.Path
import org.eclipse.core.runtime.IPath

/**
 * Needs to be created on the UI thread.
 */
class OpenOccurrenceJob(occurrence: Occurrence, page: IWorkbenchPage) extends Job("Open Occurrence Job") with HasLogger {

  //
  // We have all of this in the constructor because it's accessing objects that
  // can only be accessed on the UI thread.
  //
  logger.debug("path is : "+occurrence.path)
  val path: IPath = new Path(occurrence.path)

  val file: IFile = {
    val f = SemanticSearchPlugin.root.getFile(path);
    if (file == null || file.getRawLocation() == null) {
      SemanticSearchPlugin.root.getFileForLocation(path);
    } else f
  }

  val input = new FileEditorInput(file);
  val desc = IDE.getEditorDescriptor(occurrence.fileName)
  val part = IDE.openEditor(page, input, desc.getId())
  val editor = Helper.getTextEditor(input, part)
  val document = editor.getDocumentProvider().getDocument(editor.getEditorInput());

  def run(monitor: IProgressMonitor): IStatus = {
    val mark = MarkerHelper.createMarker(occurrence, document, file, editor)
    MarkerHelper.goToMarker(mark, editor)
    Status.OK_STATUS
  }

}