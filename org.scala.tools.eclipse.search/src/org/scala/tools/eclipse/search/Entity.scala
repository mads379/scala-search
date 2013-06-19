package org.scala.tools.eclipse.search

import org.scala.tools.eclipse.search.searching.Location

/**
 * Our own simple abstraction for Scala entities. This is abstraction
 * is used to pass information between the compiler and the components
 * that need if.
 */
trait Entity {
  def name: String
  def location: Location
}

case class Val(name: String, location: Location) extends Entity
case class Var(name: String, location: Location) extends Entity
case class Method(name: String, location: Location) extends Entity

trait TypeEntity extends Entity
case class Type(name: String, location: Location) extends TypeEntity
case class Trait(name: String, location: Location) extends TypeEntity
case class Class(name: String, location: Location) extends TypeEntity
case class Module(name: String, location: Location) extends TypeEntity
