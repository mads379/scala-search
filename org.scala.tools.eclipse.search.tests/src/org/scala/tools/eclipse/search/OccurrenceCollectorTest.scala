package org.scala.tools.eclipse.search

import org.junit.Test
import org.junit.Assert._
import scala.tools.eclipse.testsetup.TestProjectSetup
import scala.tools.eclipse.javaelements.ScalaSourceFile

object OccurrenceCollectorTest extends TestProjectSetup("aProject", bundleName= "org.scala.tools.eclipse.search.tests") {

  def occurrenceFor(word: String, occurrences: Seq[Occurrence]) = {
    occurrences.filter( _.word == word )
  }

  def occurrencesInUnit(name: String) = {
    val unit = scalaCompilationUnit(name)
    OccurrenceCollector.findOccurrences(unit)
  }

}

class OccurrenceCollectorTest {

  import OccurrenceCollectorTest._

  @Test
  def numberOfMethods() {
    occurrencesInUnit("org/example/ScalaClass.scala").fold(
        error => assertEquals(error, true, false),
        occurrences => {
          val methodOne = occurrenceFor("methodOne", occurrences)
          val methodTwo = occurrenceFor("methodTwo", occurrences)
          val methodThree = occurrenceFor("methodThree", occurrences)
          assertEquals("Should be two occurrences of methodOne %s".format(methodOne), 2, methodOne.size)
          assertEquals("Should be two occurrences of methodTwo %s".format(methodTwo), 2, methodTwo.size)
          assertEquals("Should be two occurrences of methodThree %s".format(methodThree), 2, methodThree.size)
        })
  }

  @Test
  def methodChaining() {
    occurrencesInUnit("org/example/MethodChaining.scala").fold(
        error => assertEquals(error, true, false),
        occurrences => {
          val foo = occurrenceFor("foo", occurrences)
          val bar = occurrenceFor("bar", occurrences)
          assertEquals("Should be two occurrences of foo %s".format(foo), 2, foo.size)
          assertEquals("Should be two occurrences of bar %s".format(bar), 2, bar.size)
        })
  }

  @Test def invocationAsArgument() {
    occurrencesInUnit("org/example/InvocationAsArgument.scala").fold(
        error => assertEquals(error, true, false),
        occurrences => {
          val m = occurrenceFor("methodTwo", occurrences)
          assertEquals(
              "Should be 3 occurrences of methodTwo %s".format(m), 3, m.size)
        })
  }

}
