package org.scala.tools.eclipse.search

import org.junit.Test
import org.junit.Assert._
import scala.tools.eclipse.testsetup.TestProjectSetup

class OccurrenceCollectorTest extends TestProjectSetup("aProject", bundleName= "org.scala.tools.eclipse.search.tests") {

  @Test
  def numberOfMethods() {
    val compilationUnit = scalaCompilationUnit("org/example/ScalaClass.scala")
    val occurrences = OccurrenceCollector.findOccurrences(compilationUnit)
    assertEquals("", 6, occurrences.right.toOption.map(_.size).getOrElse(0))
  }

}
