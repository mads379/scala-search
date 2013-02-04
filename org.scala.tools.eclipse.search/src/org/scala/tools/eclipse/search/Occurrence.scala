package org.scala.tools.eclipse.search

sealed abstract class OccurrenceKind
case object Declaration extends OccurrenceKind
case object Reference extends OccurrenceKind

sealed abstract class EntityKind
case object Method extends EntityKind

case class Occurrence(
    word: String,
    path: String,
    fileName: String,
    offset: Int, /* char offset from beginning of file */
    occurrenceKind: OccurrenceKind,
    entity: EntityKind) {

  override def toString = "%s in %s at char %s %s".format(word, fileName, offset.toString, occurrenceKind.toString) 

}