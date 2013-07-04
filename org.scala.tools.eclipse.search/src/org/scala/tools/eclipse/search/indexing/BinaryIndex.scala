package org.scala.tools.eclipse.search.indexing

import scala.tools.eclipse.logging.HasLogger
import scala.util.Try
import scala.util.Success
import scala.util.Failure

trait BinaryIndex extends HasLogger {

  var data = Map[String, Seq[BytecodeDeclaration]]()

  // Supposed to be invoked after searching an entire JAR.
  def add(jar: String, xs: Seq[BytecodeDeclaration]): Try[Unit] = {
    data = data + (jar -> xs)
    logger.debug(s"Found ${xs.size} hits in $jar")
    Success()
  }

  def find(jar: String, name: String): Try[Seq[BytecodeDeclaration]] = {
    val hits = data.get(jar) map { xs => xs.filter(_.name == name) }
    logger.debug(s"Searching for $name in $jar. Found $hits")
    hits map { Success.apply } getOrElse Success(Nil)
  }

}