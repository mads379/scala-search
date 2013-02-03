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
import org.eclipse.ui.ide.IDE
import org.eclipse.ui.part.MultiPageEditorPart
import org.eclipse.ui.part.ViewPart
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.ui.texteditor.MarkerUtilities
import org.scala.tools.eclipse.search.Occurrence
import org.scala.tools.eclipse.search.SemanticSearchPlugin
import org.eclipse.ui.part.FileEditorInput

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
          openOccurrence(occurrence, page).run()
        }
      }

    })

    view.getTree.addKeyListener( new KeyAdapter {
      override def keyReleased(keyEvent: KeyEvent) {
        if (keyEvent.keyCode == SWT.CR) {
          val selection = view.getSelection().asInstanceOf[IStructuredSelection]
          getSelectedOccurrence(selection) foreach { occurrence =>
            openOccurrence(occurrence, page).run()
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

  private def createMarker(occurrence: Occurrence, file: IFile, editor: ITextEditor): IMarker = {
    val marker = file.createMarker(NewSearchUI.SEARCH_MARKER)
    marker.setAttribute(IMarker.TRANSIENT, true)
    marker.setAttribute(IMarker.MESSAGE, occurrence.word)

    MarkerUtilities.setLineNumber(marker, occurrence.line);
    MarkerUtilities.setCharStart(marker, occurrence.offset);
    MarkerUtilities.setCharEnd(marker, occurrence.offset + occurrence.word.length)

    marker
  }

  private def goToMarker(mark: IMarker, editor: IEditorPart): Unit = {
    val runnable = new Runnable(){
      override def run: Unit = {
        IDE.gotoMarker(editor, mark)
      }
    }
    editor.getSite().getShell().getDisplay().asyncExec(runnable)
  }

  private def getTextEditor(input: IEditorInput, editor: IEditorPart): ITextEditor = {
    if( editor.isInstanceOf[MultiPageEditorPart] )
    {
      val multiPageEditor = editor.asInstanceOf[MultiPageEditorPart]
      val editors = multiPageEditor.findEditors(input);
      val edOp = (editors.collect {
        case x: ITextEditor =>
          multiPageEditor.setActiveEditor(x)
          x
      }).headOption
      edOp.getOrElse(throw new Exception("No text editors"))
    } else if(editor.isInstanceOf[ITextEditor] )
    {
      editor.asInstanceOf[ITextEditor]
    }
    throw new Exception("Unknown editor")
  }

  /**
   * Needs to run on the UI thread.
   */
  class OpenOccurrenceJob(occurrence: Occurrence, page: IWorkbenchPage) extends Job("Open Occurrence Job") {

    def run(monitor: IProgressMonitor): IStatus = {
      val ipath: IPath = new Path(occurrence.path)
      val file: IFile = SemanticSearchPlugin.root.getFile(ipath)

      val input = new FileEditorInput(file);
      val desc = IDE.getEditorDescriptor(occurrence.fileName)
      val part = IDE.openEditor(page, input, desc.getId())
      val editor = getTextEditor(input, part)

      val mark  = createMarker(occurrence, file, editor)
      goToMarker(mark, editor)

      Status.OK_STATUS
    }

  }

}