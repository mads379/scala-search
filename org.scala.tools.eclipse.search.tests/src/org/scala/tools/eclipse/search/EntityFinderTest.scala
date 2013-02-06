package org.scala.tools.eclipse.search

import scala.tools.eclipse.testsetup.TestProjectSetup
import org.junit.Assert._
import org.junit.Test
import org.scala.tools.eclipse.search.SymbolAbstraction.Argument
import org.scala.tools.eclipse.search.SymbolAbstraction.MethodSym
import org.scala.tools.eclipse.search.indexing.Indexer
import org.scala.tools.eclipse.search.indexing.MemoryIndex
import EntityFinderTest.scalaCompilationUnit
import scala.tools.eclipse.logging.HasLogger

object EntityFinderTest extends TestProjectSetup("aProject", bundleName= "org.scala.tools.eclipse.search.tests") {


}

class EntityFinderTest extends HasLogger {

  import EntityFinderTest._

  @Test
  def basic() {

    val index = new MemoryIndex
    val indexer = new Indexer(index)
    val finder = new EntityFinder(index)

    val scu = scalaCompilationUnit("org/example/Overloaded.scala")

    indexer.indexFile(scu)

    val occurrences = finder.occurrencesOf(MethodSym(
        "testing",
        List(List(Argument("x","String"))),
        "String"
    ))

    assertEquals("Should be two references to",2,occurrences.size)

  }

}