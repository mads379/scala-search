package org.scala.tools.eclipse.search

import scala.tools.eclipse.javaelements.ScalaSourceFile

sealed abstract class OccurrenceKind
case object Declaration extends OccurrenceKind
case object Reference extends OccurrenceKind

sealed abstract class EntityKind
case object Method extends EntityKind

case class Occurrence(
    word: String,
    file: ScalaSourceFile,
    offset: Int, /* char offset from beginning of file */
    occurrenceKind: OccurrenceKind,
    entity: EntityKind) {

  override def toString = "%s in %s at char %s %s".format(word, file.file.file.getName(), offset.toString, occurrenceKind.toString) 

}