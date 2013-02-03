package org.scala.tools.eclipse.search.indexing

import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.core.resources.IWorkspaceRoot
import org.scala.tools.eclipse.search.Occurrence
import org.scala.tools.eclipse.search.OccurrenceCollector
import org.eclipse.jdt.internal.core.JavaProject
import scala.tools.eclipse.ScalaPlugin

class Indexer(memoryIndex: MemoryIndex) extends HasLogger {

  def indexWorkspace(root: IWorkspaceRoot) = {
    root.getProjects().foreach { p =>
      ScalaPlugin.plugin.asScalaProject(p).map( proj => {
        indexProject(proj)
      }).getOrElse(logger.debug("Couldn't convert to scala project %s".format(p)))
    }
  }

  def indexProject(proj: ScalaProject) = {
    logger.debug("Indexing project %s".format(proj))
    proj.allSourceFiles.foreach {  file =>
      val path = file.getFullPath().toOSString()
      ScalaSourceFile.createFromPath(path).foreach { cu =>
        OccurrenceCollector.findOccurrences(cu).fold(
          fail => logger.debug(fail),
          occurrences => addOccurrencesInFile(path, occurrences))
      }
    }
  }

  private def addOccurrencesInFile(file: String, occurrences: Seq[Occurrence]) = {
    logger.debug("Found occurrences in file %s %s".format(file, occurrences.mkString("\n")))
    memoryIndex.addOccurrences(file, occurrences)
  }

}