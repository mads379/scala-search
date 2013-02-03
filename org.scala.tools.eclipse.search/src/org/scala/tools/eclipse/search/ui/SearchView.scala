package org.scala.tools.eclipse.search.ui

import scala.tools.eclipse.logging.HasLogger

import org.eclipse.jface.action.Action
import org.eclipse.jface.action.IAction
import org.eclipse.jface.viewers.DoubleClickEvent
import org.eclipse.jface.viewers.IDoubleClickListener
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.TreeViewer
import org.eclipse.swt.SWT
import org.eclipse.swt.events.KeyAdapter
import org.eclipse.swt.events.KeyEvent
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.widgets.Composite
import org.eclipse.ui.IWorkbenchPage
import org.eclipse.ui.part.ViewPart
import org.scala.tools.eclipse.search.Occurrence
import org.scala.tools.eclipse.search.SemanticSearchPlugin
import org.scala.tools.eclipse.search.jobs.OpenOccurrenceJob

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

}