package org.scala.tools.eclipse.search.ui

import scala.Array.canBuildFrom
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IMarker
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Path
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.IAction
import org.eclipse.jface.viewers.DoubleClickEvent
import org.eclipse.jface.viewers.IDoubleClickListener
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.TreeViewer
import org.eclipse.search.ui.NewSearchUI
import org.eclipse.swt.SWT
import org.eclipse.swt.events.KeyAdapter
import org.eclipse.swt.events.KeyEvent
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.widgets.Composite
import org.eclipse.ui.IEditorInput
import org.eclipse.ui.IEditorPart
import org.eclipse.ui.IWorkbenchPage
import org.eclipse.ui.part.MultiPageEditorPart
import org.eclipse.ui.part.ViewPart
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.ui.texteditor.MarkerUtilities
import org.scala.tools.eclipse.search.Occurrence
import org.scala.tools.eclipse.search.SemanticSearchPlugin
import org.eclipse.ui.part.FileEditorInput
import org.eclipse.ui.ide.IDE
import org.eclipse.ui.ide.IGotoMarker
import org.eclipse.ui.IEditorRegistry
import org.eclipse.ui.IEditorDescriptor
import org.eclipse.ui.internal.ide.IDEWorkbenchPlugin
import org.eclipse.ui.PartInitException
import org.eclipse.ui.internal.ide.IDEWorkbenchMessages
import org.eclipse.ui.PlatformUI
import scala.tools.eclipse.ScalaEditor
import scala.tools.eclipse.ScalaSourceFileEditor
import org.eclipse.jface.text.IDocument

/**
 * The main view for displaying search results
 */
class SearchView extends ViewPart with HasLogger {

  private var treeView: TreeViewer = _

  override def createPartControl(parent: Composite) {
    treeView = SearchView.createTreeViewer(parent, getSite().getPage())
    treeView.setContentProvider(new SearchViewContentProvider)
    treeView.setLabelProvider(new SearchViewLabelProvider)
    treeView.setInput(Map())
  }

  override def setFocus() {
    treeView.getControl().setFocus()
  }

}

object SearchView extends HasLogger {

  final val ID: String = "org.scala.tools.eclipse.search.ui.treeviewer.view"

  private def createTreeViewer(parent: Composite, page: IWorkbenchPage): TreeViewer = {
    val viewComposite = new Composite(parent, SWT.FULL_SELECTION)
    val fillLayout = new FillLayout();
    val gridData = new GridData(GridData.FILL, GridData.FILL, true, true)
    viewComposite.setLayoutData(gridData);
    fillLayout.marginHeight = 0;
    fillLayout.marginWidth = 0;
    fillLayout.spacing = 0;
    viewComposite.setLayout(fillLayout);
    val view = new TreeViewer(viewComposite, SWT.FULL_SELECTION);
    SemanticSearchPlugin.resultsView = view

    view.addDoubleClickListener(new IDoubleClickListener() {
      override def doubleClick(event: DoubleClickEvent) {
        val viewer: TreeViewer = event.getViewer().asInstanceOf[TreeViewer];
        val selection: IStructuredSelection = event.getSelection.asInstanceOf[IStructuredSelection]
        getSelectedOccurrence(selection) foreach { occurrence =>
          openOccurrence(occurrence, page).run() /* running on the UI thread.*/
        }
      }
    })

    view.getTree.addKeyListener( new KeyAdapter {
      override def keyReleased(keyEvent: KeyEvent) {
        if (keyEvent.keyCode == SWT.CR) {
          val selection = view.getSelection().asInstanceOf[IStructuredSelection]
          getSelectedOccurrence(selection) foreach { occurrence =>
            openOccurrence(occurrence, page).run() /* running on the UI thread.*/
          }
        }
      }
    })

    view
  }

  private def getSelectedOccurrence(selection: IStructuredSelection): Option[Occurrence] = {
    val element = selection.getFirstElement()
    if(element.isInstanceOf[Occurrence]) {
      Some(element.asInstanceOf[Occurrence])
    } else None
  }

  private def openOccurrence(occurrence: Occurrence, page: IWorkbenchPage): IAction = {
    new Action() {
      override def run() {
        new OpenOccurrenceJob(occurrence, page).schedule()
      }
    }
  }

  private def createMarker(occurrence: Occurrence, document: IDocument, file: IFile, editor: ITextEditor): IMarker = {
    val marker = file.createMarker(NewSearchUI.SEARCH_MARKER)
    marker.setAttribute(IMarker.TRANSIENT, true)
    marker.setAttribute(IMarker.MESSAGE, occurrence.word)

    logger.debug("Creating marker for %s".format(occurrence))


    val line = occurrence.line
    val pos = document.getLineOffset(line-1)
    val charStart = pos + occurrence.offset - 1
    val charEnd = charStart + occurrence.word.length

    MarkerUtilities.setLineNumber(marker, line)
    MarkerUtilities.setCharStart(marker, charStart)
    MarkerUtilities.setCharEnd(marker, charEnd)

    marker
  }

  private def goToMarker(mark: IMarker, editor: IEditorPart): Unit = {
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

  private def getTextEditor(input: IEditorInput, editor: IEditorPart): ITextEditor = {
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

  /**
   * Needs to be created on the UI thread.
   */
  class OpenOccurrenceJob(occurrence: Occurrence, page: IWorkbenchPage) extends Job("Open Occurrence Job") {

   //
   // We have all of this in the constructor because it's accessing objects that
   // can only be accessed on the UI thread.
   //
    logger.debug("path is : " + occurrence.path)
    val path: IPath = new Path(occurrence.path)

    val file: IFile = {
      val f = SemanticSearchPlugin.root.getFile(path);
      if( file == null || file.getRawLocation() == null ) {
          SemanticSearchPlugin.root.getFileForLocation(path);
      } else f
    }

    val input = new FileEditorInput(file);
    val desc = IDE.getEditorDescriptor(occurrence.fileName)
    val part = IDE.openEditor(page, input, desc.getId())
    val editor = getTextEditor(input, part)
    val document = editor.getDocumentProvider().getDocument(editor.getEditorInput());

    def run(monitor: IProgressMonitor): IStatus = {
      val mark  = createMarker(occurrence, document, file, editor)
      goToMarker(mark, editor)
      Status.OK_STATUS
    }

  }

}