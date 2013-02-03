package org.scala.tools.eclipse.search

import scala.tools.eclipse.logging.HasLogger
import org.eclipse.ui.plugin.AbstractUIPlugin
import org.osgi.framework.BundleContext
import org.scala.tools.eclipse.search.indexing.Indexer
import org.eclipse.core.resources.ResourcesPlugin
import org.scala.tools.eclipse.search.indexing.MemoryIndex
import org.scala.tools.eclipse.search.ui.SearchViewContentProvider
import org.eclipse.jface.viewers.TreeViewer

/**
 * Controls the plugins life-cycle.
 */
class SemanticSearchPlugin extends AbstractUIPlugin with HasLogger {

  override def start(context: BundleContext) {
    logger.debug("Starting semantic search plugin")

    SemanticSearchPlugin.indexer.indexWorkspace(SemanticSearchPlugin.root)
  }

  override def stop(context: BundleContext) {
    logger.debug("Stopping semantic search plugin")
  }

}

object SemanticSearchPlugin {

  final val index = new MemoryIndex
  final val indexer = new Indexer(index)

  /* It seems that you have to go through the view to update the content so
   * we need a handle on the view, but as the platform is responsible for
   * creating the view we need to have this annoying global variable*/
  var resultsView: TreeViewer = _

  final val PLUGIN_ID: String = "org.scala.tools.eclipse.search";

  def getPluginId(): String = PLUGIN_ID

  def root = ResourcesPlugin.getWorkspace().getRoot()

}