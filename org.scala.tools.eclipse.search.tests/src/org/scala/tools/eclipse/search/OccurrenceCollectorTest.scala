package org.scala.tools.eclipse.search

import org.junit.Test
import org.junit.Assert._
import scala.tools.eclipse.testsetup.TestProjectSetup
import scala.tools.eclipse.javaelements.ScalaSourceFile
<<<<<<< HEAD

object OccurrenceCollectorTest extends TestProjectSetup("aProject", bundleName= "org.scala.tools.eclipse.search.tests") {
=======
import org.scala.tools.eclipse.search.indexing.OccurrenceCollector
import org.scala.tools.eclipse.search.indexing.Occurrence
import java.io.File

object OccurrenceCollectorTest extends TestProjectSetup("aProject", bundleName= "org.scala.tools.eclipse.search.tests")
                                  with TestUtil {
>>>>>>> 71949254cafb660169c4a576ed29e2fe248136b1

  def occurrenceFor(word: String, occurrences: Seq[Occurrence]) = {
    occurrences.filter( _.word == word )
  }

<<<<<<< HEAD
  def occurrencesInUnit(name: String) = {
    val unit = scalaCompilationUnit(name)
    OccurrenceCollector.findOccurrences(unit)
=======
  def doWithOccurrencesInUnit(path: String*)(f: Seq[Occurrence] => Unit): Unit = {
    val unit = scalaCompilationUnit(mkPath(path:_*))
    val occurrences = OccurrenceCollector.findOccurrences(unit)
    occurrences.fold(
      error => fail(error),
      occs => f(occs))
>>>>>>> 71949254cafb660169c4a576ed29e2fe248136b1
  }

}

<<<<<<< HEAD
=======
/**
 * This tests the occurrence collector exclusively, this doesn't depend on any for of index.
 */
>>>>>>> 71949254cafb660169c4a576ed29e2fe248136b1
class OccurrenceCollectorTest {

  import OccurrenceCollectorTest._

  @Test
  def numberOfMethods() {
<<<<<<< HEAD
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
=======
    doWithOccurrencesInUnit("org","example","ScalaClass.scala") { occurrences =>
      val methodOne = occurrenceFor("methodOne", occurrences)
      val methodTwo = occurrenceFor("methodTwo", occurrences)
      val methodThree = occurrenceFor("methodThree", occurrences)
      assertEquals("Should be two occurrences of methodOne %s".format(methodOne), 2, methodOne.size)
      assertEquals("Should be two occurrences of methodTwo %s".format(methodTwo), 2, methodTwo.size)
      assertEquals("Should be two occurrences of methodThree %s".format(methodThree), 2, methodThree.size)
    }
>>>>>>> 71949254cafb660169c4a576ed29e2fe248136b1
  }

  @Test
  def methodChaining() {
<<<<<<< HEAD
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
=======
    doWithOccurrencesInUnit("org","example","MethodChaining.scala") { occurrences => 
      val foo = occurrenceFor("foo", occurrences)
      val bar = occurrenceFor("bar", occurrences)
      assertEquals("Should be two occurrences of foo %s".format(foo), 2, foo.size)
      assertEquals("Should be two occurrences of bar %s".format(bar), 2, bar.size)
    }
  }

  @Test def invocationAsArgument() {
    doWithOccurrencesInUnit("org","example","InvocationAsArgument.scala") { occurrences => 
      val m = occurrenceFor("methodTwo", occurrences)
      assertEquals("Should be 3 occurrences of methodTwo %s".format(m), 3, m.size)
    }
  }

  @Test def selectInApply() {
    doWithOccurrencesInUnit("org","example","SelectInApply.scala") { occurrences =>
      val x = occurrenceFor("x", occurrences)
      assertEquals("Should be 2 occurrences of x %s".format(x), 2, x.size)
    }
  }

  @Test def stringInterpolation() {
    doWithOccurrencesInUnit("org","example","StringInterpolation.scala") { occurrences =>
      val x = occurrenceFor("x", occurrences)
      assertEquals("Should be 2 occurrences of x %s".format(x), 2, x.size)
    }
  }

  @Test def annotationsOnMethods() {
    doWithOccurrencesInUnit("org","example", "Annotations.scala") { occurrences =>
      val x = occurrenceFor("IOException", occurrences)
      assertEquals("Should be 1 occurrences of IOException %s".format(x), 1, x.size)
    }
>>>>>>> 71949254cafb660169c4a576ed29e2fe248136b1
  }

}
