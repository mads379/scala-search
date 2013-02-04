package org.scala.tools.eclipse.search

import org.eclipse.jface.action.IAction
import org.eclipse.ui.IStartup
import org.eclipse.ui.IWorkbenchWindow
import org.eclipse.ui.IWorkbenchWindowActionDelegate
import org.eclipse.ui.PlatformUI
import org.scala.tools.eclipse.search.ui.SearchView
import org.eclipse.jface.viewers.ISelection
import org.scala.tools.eclipse.search.actions.ShowSemanticSearchAction

/**
 * This class instructs Eclipse to start the plugin when Eclipse
 * is booted.
 */
class SemanticSearchStartup extends IStartup {

  def earlyStartup(): Unit = {
    PlatformUI.getWorkbench().getDisplay().asyncExec(new ShowSemanticSearchAction)
  }

}

