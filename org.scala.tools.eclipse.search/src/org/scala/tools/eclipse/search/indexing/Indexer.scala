package org.scala.tools.eclipse.search.indexing

import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.core.resources.IWorkspaceRoot
import org.scala.tools.eclipse.search.Occurrence
import org.scala.tools.eclipse.search.OccurrenceCollector
import org.eclipse.jdt.internal.core.JavaProject
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.core.resources.IFile

class Indexer(memoryIndex: MemoryIndex) extends HasLogger {

  def indexWorkspace(root: IWorkspaceRoot) = {
    root.getProjects().foreach { p =>
      if (p.isOpen()) {
        ScalaPlugin.plugin.asScalaProject(p).map( proj => {
          indexProject(proj)
        }).getOrElse(logger.debug("Couldn't convert to scala project %s".format(p)))
      } else {
        logger.debug("Skipping %s because it is closed".format(p))
      }
    }
  }

  def indexProject(proj: ScalaProject) = {
    logger.debug("Indexing project %s".format(proj))
    proj.allSourceFiles.foreach { indexFile }
  }

  def indexFile(file: IFile): Unit = {
    val path = file.getFullPath().toOSString()
    ScalaSourceFile.createFromPath(path).foreach { cu =>
        indexFile(cu)
    }
  }

  def indexFile(cu: ScalaSourceFile): Unit = {
    OccurrenceCollector.findOccurrences(cu).fold(
      fail => logger.debug(fail),
      occurrences => addOccurrencesInFile(cu.file.file.getAbsolutePath(), occurrences))
  }

  private def addOccurrencesInFile(file: String, occurrences: Seq[Occurrence]) = {
    memoryIndex.removeOccurrences(file)
    memoryIndex.addOccurrences(file, occurrences)
  }

}