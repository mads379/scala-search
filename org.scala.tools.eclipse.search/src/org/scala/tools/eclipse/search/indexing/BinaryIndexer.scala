package org.scala.tools.eclipse.search.indexing

import scala.tools.eclipse.logging.HasLogger
import java.io.File
import scala.util.Try
import scala.util.Success

/**
 * Component used to index binaries.
 * 
 * Uses the ClassfileTraverser to read classfiles and find all
 * declarations.
 * 
 * Stores the declarations in the given BinaryIndex.
 * 
 */
class BinaryIndexer(val index: BinaryIndex) extends HasLogger  {

  /**
   * Indexes all the classfiles in the given `jar` and adds
   * declared types (classes, traits, etc)
   */
  def index(jar: File): Try[Unit] = {
    logger.debug(s"Indexing ${jar.getAbsolutePath}")
    val declarations = ClassfileTraverser.findDeclarations(jar)
    index.add(jar.getName(), declarations)
    Success()
  }

}