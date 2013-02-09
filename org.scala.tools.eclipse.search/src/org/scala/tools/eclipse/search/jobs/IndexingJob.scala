package org.scala.tools.eclipse.search
package jobs

import org.eclipse.core.resources.WorkspaceJob
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.scala.tools.eclipse.search.SemanticSearchPlugin
import org.eclipse.core.runtime.Status
import scala.tools.eclipse.logging.HasLogger

/**
 * Asynchronous job that indexing the entire workspace. This is executed once
 * upon startup of Eclipse.
 */
class IndexingJob extends WorkspaceJob("Initial Indexing Job")
                     with HasLogger {

  override def runInWorkspace(monitor: IProgressMonitor): IStatus = {
    // TODO: Should report progress somehow.
    logger.debug("Started Initial Indexing Job")
    val elapsed = timed {
      SemanticSearchPlugin.indexer.indexWorkspace(SemanticSearchPlugin.root)
    }
    logger.debug("Initial Index Should be Done. Took %s seconds".format(elapsed))
    Status.OK_STATUS
  }

}