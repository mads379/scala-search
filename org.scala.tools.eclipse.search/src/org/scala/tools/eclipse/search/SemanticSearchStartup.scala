package org.scala.tools.eclipse.search

import org.eclipse.jface.action.IAction
import org.eclipse.ui.IStartup
import org.eclipse.ui.IWorkbenchWindow
import org.eclipse.ui.IWorkbenchWindowActionDelegate
import org.eclipse.ui.PlatformUI
import org.scala.tools.eclipse.search.ui.SearchView
import org.eclipse.jface.viewers.ISelection

/**
 * This class instructs Eclipse to start the plugin when Eclipse
 * is booted.
 */
class SemanticSearchStartup extends IStartup {

  def earlyStartup(): Unit = {
    PlatformUI.getWorkbench().getDisplay().asyncExec(new ShowSemanticSearchAction)
  }

  private class ShowSemanticSearchAction
        extends IWorkbenchWindowActionDelegate
           with Runnable {

    override def run(action: IAction) = run()

    override def run() {
      for {
        wb   <- Option(PlatformUI.getWorkbench())
        wbW  <- Option(wb.getActiveWorkbenchWindow())
        page <- Option(wbW.getActivePage()) 
      } page.showView(SearchView.ID)
    }

    override def dispose() {}

    override def init(window: IWorkbenchWindow) {}

    override def selectionChanged(action: IAction, selected: ISelection){}

  }

}

