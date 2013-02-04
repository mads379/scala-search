package org.scala.tools.eclipse.search.actions

import org.eclipse.ui.IWorkbenchWindowActionDelegate
import org.eclipse.jface.action.IAction
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.IWorkbenchWindow
import org.eclipse.jface.viewers.ISelection
import org.scala.tools.eclipse.search.ui.SearchView

class ShowSemanticSearchAction
    extends IWorkbenchWindowActionDelegate
       with Runnable {

  override def run(action: IAction) = run()

  override def run() {
    for {
      wb <- Option(PlatformUI.getWorkbench())
      wbW <- Option(wb.getActiveWorkbenchWindow())
      page <- Option(wbW.getActivePage())
    } page.showView(SearchView.ID)
  }

  override def dispose() {}

  override def init(window: IWorkbenchWindow) {}

  override def selectionChanged(action: IAction, selected: ISelection) {}

}