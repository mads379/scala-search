package org.scala.tools.eclipse.search
package searching

import org.scala.tools.eclipse.search.indexing.Index
import scala.tools.eclipse.ScalaPlugin
import org.scala.tools.eclipse.search.ErrorReporter
import scala.tools.eclipse.logging.HasLogger
import org.scala.tools.eclipse.search.indexing.SearchFailure
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.NullProgressMonitor
import scala.tools.eclipse.ScalaProject
import org.scala.tools.eclipse.search.indexing.Occurrence
import scala.reflect.internal.util.SourceFile

/**
 * Component that provides various methods related to finding Scala entities.
 */
class Finder(index: Index, reporter: ErrorReporter) extends HasLogger {

  private val finder: ProjectFinder = new ProjectFinder

  /**
   * Find all super-classes of the type at the given location.
   *
   * - Exact matches are passed to the `hit` function.
   * - Potential matches are passed to the `potentialHit` function. A potential
   *   match is when the index reports and occurrence but we can't type-check
   *   the given point to see if it is an exact match.
   * - Should any errors occur in the Index that we can't handle, the failures
   *   are passed to the `errorHandler` function.
   */
  def findAllSuperclasses(location: Location, monitor: IProgressMonitor = new NullProgressMonitor)
                         (hit: ExactHit => Unit,
                          potentialHit: PotentialHit => Unit = _ => (),
                          errorHandler: SearchFailure => Unit = _ => ()): Unit = {

    val allScala = relevantProjects(location)

    // Given a single super-type, find the declaration
    def findDeclarationOfType(name: String, comparator: SymbolComparator): Unit = {
      val (occurrences, errors) = index.findDeclarations(name, allScala)
      errors foreach errorHandler
      val it = occurrences.iterator
      while (it.hasNext && !monitor.isCanceled) {
        val occurrence = it.next
        val loc = Location(occurrence.file, occurrence.offset)
        comparator.isSameAs(loc) match {
          case Same         => hit(occurrence.toExactHit)
          case PossiblySame => potentialHit(occurrence.toPotentialHit)
          case NotSame      => logger.debug(s"$occurrence wasn't the same.")
        }
      }
    }

    // For each super-type, find the declaration
    def process(types: Seq[(String, SymbolComparator)]): Unit = {
      val it = types.iterator
      while (it.hasNext && !monitor.isCanceled()) {
        val (name, comparator) = it.next
        monitor.subTask(s"Finding declaration of $name")
        findDeclarationOfType(name, comparator) 
        monitor.worked(1)
      }
    }

    // Get all the super-types of the declared entity at the given location
    location.cu.withSourceFile { (sf, pc) =>
      val spc = new SearchPresentationCompiler(pc)
      for {
        types <- spc.superTypesOfEntityAt(location) onEmpty reporter.reportError(symbolErrMsg(location, sf))
      } {
        monitor.beginTask("Typechecking for exact matches", types.size)
        process(types)
        monitor.done()
      }
    }(reporter.reportError(s"Could not access source file ${location.cu.file.path}"))
  }

  /**
   * Find all subclasses of the type at the given location.
   *
   * - Exact matches are passed to the `hit` function.
   * - Potential matches are passed to the `potentialHit` function. A potential
   *   match is when the index reports and occurrence but we can't type-check
   *   the given point to see if it is an exact match.
   * - Should any errors occur in the Index that we can't handle, the failures
   *   are passed to the `errorHandler` function.
   */
  def findAllSubclasses(location: Location, monitor: IProgressMonitor = new NullProgressMonitor)
                       (hit: ExactHit => Unit,
                        potentialHit: PotentialHit => Unit = _ => (),
                        errorHandler: SearchFailure => Unit = _ => ()): Unit = {

    val allScala = relevantProjects(location)

    def process(comparator: SymbolComparator,
                spc: SearchPresentationCompiler,
                occurrences: Seq[Occurrence]) = {

      val it = occurrences.iterator
      while (it.hasNext && !monitor.isCanceled()) {
        val occurrence = it.next
        monitor.subTask(s"Checking ${occurrence.file.file.name}")
        val loc = Location(occurrence.file, occurrence.offset)
        comparator.isSameAs(loc) match {
          case Same =>
            spc.declarationContaining(loc).map{
              o => hit(o.toExactHit)
            }.getOrElse(logger.debug(s"Couldn't find declaration"))
          case PossiblySame =>
            spc.declarationContaining(loc).map{ o =>
              potentialHit(o.toPotentialHit)
            }.getOrElse(logger.debug(s"Couldn't find declaration"))
          case NotSame =>
            logger.debug(s"$occurrence wasn't the same.")
        }
      }
    }

    location.cu.withSourceFile { (sf, pc) =>
      val spc = new SearchPresentationCompiler(pc)
      for {
        comparator <- spc.comparator(location) onEmpty reporter.reportError(comparatorErrMsg(location, sf))
        name       <- spc.nameOfEntityAt(location) onEmpty reporter.reportError(symbolErrMsg(location, sf))
      } {
        val (occurrences, failures) = index.findOccurrencesInSuperPosition(name, allScala)
        failures.foreach(errorHandler)
        monitor.beginTask("Typechecking for exact matches", occurrences.size)
        process(comparator, spc, occurrences)
        monitor.done()
      }
    }(reporter.reportError(s"Could not access source file ${location.cu.file.path}"))

  }

  /**
   * Find all occurrences of the entity at the given location.
   *
   * - Exact matches are passed to the `hit` function.
   * - Potential matches are passed to the `potentialHit` function. A potential
   *   match is when the index reports and occurrence but we can't type-check
   *   the given point to see if it is an exact match.
   * - Should any errors occur in the Index that we can't handle, the failures
   *   are passed to the `errorHandler` function.
   */
  def occurrencesOfEntityAt(location: Location, monitor: IProgressMonitor = new NullProgressMonitor)
                           (hit: ExactHit => Unit,
                            potentialHit: PotentialHit => Unit = _ => (),
                            errorHandler: SearchFailure => Unit = _ => ()): Unit = {

    val allScala = relevantProjects(location)

    def process(comparator: SymbolComparator,
                spc: SearchPresentationCompiler,
                occurrences: Seq[Occurrence]) = {
      val it = occurrences.iterator
      while (it.hasNext && !monitor.isCanceled()) {
        val occurrence = it.next
        monitor.subTask(s"Checking ${occurrence.file.file.name}")
        val loc = Location(occurrence.file, occurrence.offset)
        comparator.isSameAs(loc) match {
          case Same         => hit(occurrence.toExactHit)
          case PossiblySame => potentialHit(occurrence.toPotentialHit)
          case NotSame      => logger.debug(s"$occurrence wasn't the same.")
        }
        monitor.worked(1)
      }
    }

    // Get the symbol under the cursor. Use it to find other occurrences.
    location.cu.withSourceFile { (sf, pc) =>
      val spc = new SearchPresentationCompiler(pc)
      for {
        comparator <- spc.comparator(location) onEmpty reporter.reportError(comparatorErrMsg(location, sf))
        names      <- spc.possibleNamesOfEntityAt(location) onEmpty reporter.reportError(symbolErrMsg(location, sf))
      } {
        val (occurrences, failures) = index.findOccurrences(names, allScala)
        failures.foreach(errorHandler)
        monitor.beginTask("Typechecking for exact matches", occurrences.size)
        process(comparator, spc, occurrences)
        monitor.done()
      }
    }(reporter.reportError(s"Could not access source file ${location.cu.file.path}"))
  }

  private def relevantProjects(loc: Location): Set[ScalaProject] = {
    val enclosingProject = loc.cu.scalaProject.underlying
    val all =  finder.projectClosure(enclosingProject)
    all.map(ScalaPlugin.plugin.asScalaProject(_)).flatten
  }

  private def comparatorErrMsg(location: Location, sf: SourceFile) =
    s"Couldn't get comparator based on symbol at ${location.offset} in ${sf.file.path}"

  private def symbolErrMsg(location: Location, sf: SourceFile) =
    s"Couldn't get name of symbol at ${location.offset} in ${sf.file.path}"
}
