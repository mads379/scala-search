package org.scala.tools.eclipse.search

import scala.tools.eclipse.logging.HasLogger
import org.eclipse.ui.plugin.AbstractUIPlugin
import org.osgi.framework.BundleContext
import org.scala.tools.eclipse.search.indexing.Indexer
import org.eclipse.core.resources.ResourcesPlugin
import org.scala.tools.eclipse.search.indexing.MemoryIndex
import org.scala.tools.eclipse.search.ui.SearchViewContentProvider
import org.eclipse.jface.viewers.TreeViewer
import org.eclipse.core.resources.IFile
import org.scala.tools.eclipse.search.jobs.UpdateIndexJob
import org.scala.tools.eclipse.search.jobs.IndexingJob
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.core.resources.WorkspaceJob

/**
 * Controls the plugins life-cycle.
 */
class SemanticSearchPlugin extends AbstractUIPlugin with HasLogger {

  import SemanticSearchPlugin._

  override def start(context: BundleContext) {
    logger.debug("Starting semantic search plugin")

    // Initial index of workspace
    initialIndexJob.schedule()
    initialIndexJob.setPriority(Job.LONG) // long running job

    // Background job that periodically updates the index.
    indexUpdateJob.setSystem(true)
    indexUpdateJob.schedule(updateInterval)
  }

  override def stop(context: BundleContext) {
    logger.debug("Stopping semantic search plugin")
    initialIndexJob.cancel()
    indexUpdateJob.cancel()
  }

}

object SemanticSearchPlugin extends HasLogger {

  final val initialIndexJob = new IndexingJob()
  final val indexUpdateJob = new UpdateIndexJob()

  private final val index = new MemoryIndex
  final val indexer = new Indexer(index)
  final val entityFinder = new EntityFinder(index)

  /* It seems that you have to go through the view to update the content so
   * we need a handle on the view, but as the platform is responsible for
   * creating the view we need to have this annoying global variable*/
  var resultsView: TreeViewer = _

  final val PLUGIN_ID: String = "org.scala.tools.eclipse.search"

  def getPluginId(): String = PLUGIN_ID

  def root = ResourcesPlugin.getWorkspace().getRoot()

  def isIndexable(file: IFile): Boolean = {
    // TODO: At some point we want to make the acceptable files extensible.
    // such that frameworks such as play can have their template files indexed.
    file.getFileExtension() == "scala"
  }

  def isInitialIndexRunning: Boolean = {
    (initialIndexJob.getState() == org.eclipse.core.runtime.jobs.Job.WAITING || 
     initialIndexJob.getState() == org.eclipse.core.runtime.jobs.Job.RUNNING)
  }

  def updateInterval: Long = 5000

  /**
   *  Used to figure out how much data the index contains.
   */
  def logIndexStats: Unit = {
    val occurrences = index.all
    logger.debug("Index contains %s occurrences".format(occurrences.size))
  }

}