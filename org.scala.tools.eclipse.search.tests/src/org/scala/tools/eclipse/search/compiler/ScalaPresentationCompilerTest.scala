package org.scala.tools.eclipse.search.compiler

import org.scala.tools.eclipse.search.TestUtil
import org.scala.tools.eclipse.search.searching.SourceCreator
import org.junit.Test
import java.util.concurrent.CountDownLatch
import org.scala.tools.eclipse.search.FileChangeObserver
import scala.reflect.internal.util.OffsetPosition
import scala.tools.nsc.interactive.Response
import org.junit.Assert
import scala.tools.eclipse.logging.HasLogger
import org.junit.After

/**
 * Tests that makes sure that the compiler works as expected. This helps us find
 * regressions in the compiler in our nightly builds etc.
 */
class ScalaPresentationCompilerTest extends HasLogger {

  import ScalaPresentationCompilerTest._

  val project = Project("ScalaPresentationCompilerTest-askTypeAt-overloaded")

  @After
  def deleteProject() {
    project.delete
  }

  @Test
  def askTypeAt_overloadedMethod {

    val latch = new CountDownLatch(2)
    val observer = FileChangeObserver(project.scalaProject)(onAdded = _ => latch.countDown)

    val sourceA = project.create("A.scala") {"""
      class A {
        def askO|ption[A](op: () => A): Option[A] = askOption(op, 10000)
        def askOption[A](op: () => A, timeout: Int): Option[A] = None
      }
    """}

    val sourceB = project.create("B.scala") {"""
      object B {
        val a = new A
        a.askO|ption { () =>
          "hi there"
        }
      }
    """}

    latch.await(10,java.util.concurrent.TimeUnit.SECONDS)

    @volatile var overloaded = false
    @volatile var isMethod = false

    sourceA.unit.doWithSourceFile { (sf, pc) =>
      val typed = new Response[pc.Tree]
      val pos = new OffsetPosition(sf, sourceA.markers.head)
      pc.askTypeAt(pos, typed)
      typed.get.fold(
        tree => pc.askOption { () => isMethod = tree.symbol.isMethod },
        _ => Assert.fail("Couldn't get the type at point in sourceA"))
    }

    Assert.assertTrue("The definition should be a method symbol", isMethod)

    // check if we never get an overloaded method symbol
    sourceB.unit.doWithSourceFile { (sf, pc) =>
      val pos = new OffsetPosition(sf, sourceB.markers.head)
      val typed = new Response[pc.Tree]
      pc.askTypeAt(pos, typed)
      typed.get.fold(
        tree => pc.askOption { () => overloaded = tree.symbol.isOverloaded},
        _ => Assert.fail("Couldn't get the type"))
    }

    Assert.assertEquals("It should never return an overloaded symbol", false, overloaded)

    observer.stop
  }


}

object ScalaPresentationCompilerTest
  extends TestUtil
     with SourceCreator
