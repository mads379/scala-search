package org.scala.tools.eclipse.search

import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.collection.mutable.Map
import scala.tools.eclipse.logging.HasLogger
import scala.collection.mutable.Buffer

/**
 * Fun little in-memory implementation of an index. This is not
 * thread-safe. Just using it so I can work on other parts of the
 * plug-in while I figure out which technology to base the index on.
 */
class MemoryIndex extends HasLogger {

  private val files: Map[String, Map[String, Buffer[Occurrence]]] = Map()

  def addOccurrences(path: String, occurrences: Seq[Occurrence]) {
    val index = files.get(path).getOrElse {
      val newIndex = Map[String, Buffer[Occurrence]]()
      files += (path -> newIndex)
      newIndex
    }
    // add each occurrence.
    occurrences foreach { o =>
      val seq = index.get(o.word).getOrElse {
        val newSeq = Buffer[Occurrence]()
        index += (o.word -> newSeq)
        newSeq
      }
      seq += o
    }
  }

  def lookup(word: String): Iterable[Occurrence] = {
    logger.debug(files)
    files.values.flatMap { index => 
      index.getOrElse(word, Buffer())
    }
  }
}