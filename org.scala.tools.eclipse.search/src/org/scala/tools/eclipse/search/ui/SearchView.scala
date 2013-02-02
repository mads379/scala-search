package org.scala.tools.eclipse.search.ui

import scala.tools.eclipse.logging.HasLogger

import org.eclipse.jface.viewers.TreeViewer
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.widgets.Composite
import org.eclipse.ui.part.ViewPart

/**
 * The main view for displaying search results
 */
class SearchView extends ViewPart with HasLogger /*with ITreeViewerListener*/ {

  private var treeView: TreeViewer = _

  override def createPartControl(parent: Composite) {
    treeView = SearchView.createTreeViewer(parent)
    treeView.setContentProvider(new SearchViewContentProvider)
    treeView.setLabelProvider(new SearchViewLabelProvider)
    treeView.setInput(Map())
  }

  override def setFocus() {
    treeView.getControl().setFocus()
  }

}

object SearchView {

  final val ID: String = "org.scala.tools.eclipse.search.ui.treeviewer.view"

  private def createTreeViewer(parent: Composite): TreeViewer = {
    val viewComposite = new Composite(parent, SWT.FULL_SELECTION)
    val fillLayout = new FillLayout();
    val gridData = new GridData(GridData.FILL, GridData.FILL, true, true)
    viewComposite.setLayoutData(gridData);
    fillLayout.marginHeight = 0;
    fillLayout.marginWidth = 0;
    fillLayout.spacing = 0;
    viewComposite.setLayout(fillLayout);
    new TreeViewer(viewComposite, SWT.FULL_SELECTION);
  }

}