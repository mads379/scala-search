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
    line: Int,
    offset: Int,
    occurrenceKind: OccurrenceKind,
    entity: EntityKind) {
  
  override def toString = "%s in %s on (%s,%s) %s".format(word, fileName, line.toString, offset.toString, occurrenceKind.toString) 
  
}