package org.scala.tools.eclipse.search

sealed abstract class OccurrenceKind
case object Declaration extends OccurrenceKind
case object Reference extends OccurrenceKind

sealed abstract class EntityKind
case object Method extends EntityKind

case class Occurrence(
    word: String,
    file: String,
    line: Int,
    offset: Int,
    occurrenceKind: OccurrenceKind,
    entity: EntityKind)