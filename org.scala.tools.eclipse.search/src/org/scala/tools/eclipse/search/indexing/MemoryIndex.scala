package org.scala.tools.eclipse.search.indexing

import scala.collection.mutable.Map
import scala.tools.eclipse.logging.HasLogger
import scala.collection.mutable.Buffer
import org.scala.tools.eclipse.search.Occurrence

/**
 * Fun little in-memory implementation of an index. This is not
 * thread-safe. Just using it so I can work on other parts of the
 * plug-in while I figure out which technology to base the index on.
 */
class MemoryIndex extends HasLogger {

  private var lock = new Object()
  private val files: Map[String, Map[String, Buffer[Occurrence]]] = Map()

  def addOccurrences(path: String, occurrences: Seq[Occurrence]) = lock.synchronized {
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

  def removeOccurrences(path: String) = lock.synchronized {
    files -= path
  }

  def lookup(word: String): Seq[Occurrence] = lock.synchronized {
    files.values.flatMap { index =>
      index.getOrElse(word, Buffer())
    }.toSeq
  }

  def all: Seq[Occurrence]= lock.synchronized {
    files.values.flatMap(_.values).flatten.toSeq
  }
}