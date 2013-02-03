package org.scala.tools.eclipse.search.ui

import org.eclipse.ui.IEditorInput
import org.eclipse.ui.IEditorPart
import org.eclipse.ui.part.MultiPageEditorPart
import scala.tools.eclipse.ScalaSourceFileEditor
import org.eclipse.ui.texteditor.ITextEditor
import scala.tools.eclipse.ScalaEditor
import org.scala.tools.eclipse.search.Occurrence
import org.eclipse.jface.text.IDocument
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IMarker
import org.eclipse.search.ui.NewSearchUI
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.ui.texteditor.MarkerUtilities
import org.eclipse.ui.ide.IGotoMarker
import org.eclipse.ui.ide.IDE

/**
 * Contains various small helper methods that makes it easier to
 * deal with the eclipse APIs
 */
object Helper {

  /**
   * Given an "input" editor and part of an editor try to get the
   * entire ITextEditor object. 
   */
  def getTextEditor(input: IEditorInput, editor: IEditorPart): ITextEditor = {
    editor match {
      case multi: MultiPageEditorPart => {
        val editors = multi.findEditors(input);
        val edOp = (editors.collect {
          case x: ITextEditor =>
            multi.setActiveEditor(x)
            x
        }).headOption
        edOp.getOrElse(throw new Exception("No text editors"))
      }
      case ed: ScalaSourceFileEditor => ed
      case ed: ScalaEditor => ed
      case ed: ITextEditor => ed
      case x => throw new Exception("Unknown editor %s".format(x))
    }
  }
  
}

/**
 * Methods that makes it easier to deal with markers.
 */
object MarkerHelper extends HasLogger {

  def createMarker(occurrence: Occurrence, document: IDocument, file: IFile, editor: ITextEditor): IMarker = {
    val marker = file.createMarker(NewSearchUI.SEARCH_MARKER)
    marker.setAttribute(IMarker.TRANSIENT, true)
    marker.setAttribute(IMarker.MESSAGE, occurrence.word)
    val line = occurrence.line
    val pos = document.getLineOffset(line-1)
    val charStart = pos + occurrence.offset - 1
    val charEnd = charStart + occurrence.word.length
    MarkerUtilities.setLineNumber(marker, line)
    MarkerUtilities.setCharStart(marker, charStart)
    MarkerUtilities.setCharEnd(marker, charEnd)
    marker
  }

  def goToMarker(mark: IMarker, editor: IEditorPart): Unit = {
    val runnable = new Runnable(){
      override def run: Unit = {
        if (editor.isInstanceOf[IGotoMarker]) {
          editor.asInstanceOf[IGotoMarker].gotoMarker(mark)
        } else {
          editor.getAdapter(classOf[IGotoMarker]).asInstanceOf[IGotoMarker].gotoMarker(mark)
        }
        IDE.gotoMarker(editor, mark)
      }
    }
    editor.getSite().getShell().getDisplay().asyncExec(runnable)
  }
}