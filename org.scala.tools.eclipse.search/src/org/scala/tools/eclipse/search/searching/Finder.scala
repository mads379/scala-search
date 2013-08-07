package org.scala.tools.eclipse.search
package searching

import scala.Option.option2Iterable
import scala.reflect.internal.util.SourceFile
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.logging.HasLogger

import org.eclipse.core.runtime.IProgressMonitor
import org.scala.tools.eclipse.search.Entity
import org.scala.tools.eclipse.search.ErrorHandlingOption
import org.scala.tools.eclipse.search.ErrorReporter
import org.scala.tools.eclipse.search.TypeEntity
import org.scala.tools.eclipse.search.indexing.Index
import org.scala.tools.eclipse.search.indexing.Occurrence
import org.scala.tools.eclipse.search.indexing.SearchFailure

/**
 * Component that provides various methods related to finding Scala entities.
 *
 * Instances of this class should not be created manually but rather accessed
 * through the global instance in `SearchPlugin.finder`.
 *
 * To use the API you will want to first get the Scala entity at a given locaiton,
 * like so
 *
 *      val loc = Location(...)
 *      finder.entityAt(loc)
 *
 * The Entity instance can give you some basic information about the entity and should
 * be used in further queries if needed. For more information about Enity, read the
 * associated Scala Doc.
 *
 * Here's an example of how to find all occurrence of an entity:
 *
 *      finder.entityAt(loc) map { entity =>
 *        finder.findSubtypes(findSubtypes, ...)(
 *          handler = hit => hit match {
 *            case Certain(entity) => // ...
 *            case Uncertain(entity) => //...
 *          },
 *          errorHandler = failtures => // ... deal with failures.
 *        )
 *      }
 *
 */
class Finder(index: Index, reporter: ErrorReporter) extends HasLogger {

  private val finder: ProjectFinder = new ProjectFinder

  /**
   * Find the Entity at the given Location. Returns
   * None if it couldn't type-check the given
   * location.
   */
  def entityAt(loc: Location): Option[Entity] = {
    loc.cu.withSourceFile { (sf, pc) =>
      val spc = new SearchPresentationCompiler(pc)
      spc.entityAt(loc)
    }(None)
  }

  /**
   * Find all supertypes of the given `entity`. The handler recieves
   * a Confidence[TypeEntity] for each supertype. See Confidence
   * ScalaDoc for more information.
   *
   * Errors are passed to `errorHandler`.
   */
  def findSupertypes(entity: TypeEntity, monitor: IProgressMonitor)
                    (handler: Confidence[TypeEntity] => Unit,
                     errorHandler: SearchFailure => Unit = _ => ()): Unit = {

    def getTypeEntity(hit: Hit): Option[TypeEntity] =
      entityAt(Location(hit.cu, hit.offset)) collect { case x: TypeEntity => x }

    // Given a single super-type, find the declaration
    def findDeclarationOfType(name: String, comparator: SymbolComparator): Unit = {
      val (occurrences, errors) = index.findDeclarations(name, relevantProjects(entity.location))
      errors foreach errorHandler
      for ( occurrence <- occurrences if !monitor.isCanceled) {
        val loc = Location(occurrence.file, occurrence.offset)
        comparator.isSameAs(loc) match {
          case Same         => getTypeEntity(occurrence.toHit) map Certain.apply foreach handler
          case PossiblySame => getTypeEntity(occurrence.toHit) map Uncertain.apply foreach handler
          case NotSame      => logger.debug(s"$occurrence wasn't the same.")
        }
      }
    }

    // For each super-type, find the declaration
    def process(types: Seq[(String, SymbolComparator)]): Unit = {
      for (i <- types if !monitor.isCanceled) {
        val (name, comparator) = i
        monitor.subTask(s"Finding declaration of $name")
        findDeclarationOfType(name, comparator)
        monitor.worked(1)
      }
    }

    // Get all the super-types of the declared entity at the given location
    entity.location.cu.withSourceFile { (sf, pc) =>
      val spc = new SearchPresentationCompiler(pc)
      for {
        types <- spc.superTypesOf(entity) onEmpty reporter.reportError(symbolErrMsg(entity.location, sf))
      } {
        monitor.beginTask("Typechecking for exact matches", types.size)
        process(types)
      }
      monitor.done()
    }(reporter.reportError(s"Could not access source file ${entity.location.cu.file.path}"))


  }

  /**
   * Find all subtypes of the given `entity`. The handler recieves
   * a Confidence[TypeEntity] for each subtype. See Confidence
   * ScalaDoc for more information.
   *
   * Errors are passed to `errorHandler`.
   */
  def findSubtypes(entity: TypeEntity, monitor: IProgressMonitor)
                  (handler: Confidence[TypeEntity] => Unit,
                   errorHandler: SearchFailure => Unit = _ => ()): Unit = {
    /*
     * We find the sub-types in the following way
     *
     * 1. Use the Index to find all places where the given type is
     *    mention in the super-position of the declaration of another
     *    type. I.e. we just use the name of the type, so if we have
     *    `trait |Foo[A]` or `trait StringFoo extends |Foo[String]`
     *    it will find all the places where `Foo` is mentioned.
     *
     *    We throw away the type parameters because when searching for
     *    subtypes of Foo[A] then we also want to find Foo[String].
     *
     * 2. For each hit we get a pc.Type instance.
     *
     *    In the example `trait StringFoo extends Foo[String]` we get
     *    the type of Foo[String].
     *
     *    Now there are two cases. If the type we want to find sub-types
     *    of is a generic type, i.e. Foo[A] then we can't just use 
     *    =:= to compare the types since Foo[A] =:= Foo[String] is false.
     *    In this case we need to get a hold of the generic type when comparing
     *    i.e. if f = typeOf[Foo[String]] then t.typeSymbol.typeOfThis
     *
     *    The other case is that we have a concrete type, i.e. Foo[String],
     *    then we only want to find other subtypes of Foo[String] hence we can
     *    use =:= on the types directly
     *
     * 3. For each exact hit, get the TypeEntity that contains the hit
     *
     */

    val location = Location(entity.location.cu, entity.location.offset)

    // Step 1
    def getOccurrencesInSuperPosition: Seq[Occurrence] = {
      val (occurrences, failures) = index.findOccurrencesInSuperPosition(entity.name, relevantProjects(location))
      failures.foreach(errorHandler)
      occurrences
    }

    // Step2 - The actual logic is taken care of by the SearchPresentationCompiler
    def comparator(spc: SearchPresentationCompiler) = spc.subtypeComparator(location)

    // Step 3
    def getDeclarationContaining(hit: Hit): Option[TypeEntity] = {
      hit.cu.withSourceFile { (sf,pc) =>
        val spc = new SearchPresentationCompiler(pc)
        for {
          declaration <- spc.declarationContaining(Location(hit.cu, hit.offset))
          declEntity <- entityAt(Location(declaration.file, declaration.offset))
          typeEntity <- declEntity match {
            case x: TypeEntity => Some(x)
            case _ => None
          }
        } yield typeEntity
      }(None)
    }

    // tie it togehter
    entity.location.cu.withSourceFile { (sf, pc) =>
      val spc = new SearchPresentationCompiler(pc)
      for {
        comp <- comparator(spc) onEmpty logger.debug("Couldn't get comparator for " + location)
      } {
        val occurrences = getOccurrencesInSuperPosition
        monitor.beginTask("Typechecking for exact matches", occurrences.size)
        processSame(occurrences, monitor, comp, hit => hit match {
          case Certain(hit)   => getDeclarationContaining(hit) map Certain.apply foreach handler
          case Uncertain(hit) => getDeclarationContaining(hit) map Uncertain.apply foreach handler
        })
      }
    }(reporter.reportError(s"Could not access source file ${location.cu.file.path}"))
    monitor.done()
  }

  /**
   * Find all occurrences of the given `entity`, that is, references
   * and declarations that match.
   *
   * The handler recieves a Confidence[Hit] for each occurrence. See
   * Confidence ScalaDoc for more information.
   *
   * Errors are passed to `errorHandler`.
   */
  def occurrencesOfEntityAt(entity: Entity, monitor: IProgressMonitor)
                           (handler: Confidence[Hit] => Unit,
                            errorHandler: SearchFailure => Unit = _ => ()): Unit = {

    // Get the symbol under the cursor. Use it to find other occurrences.
    entity.location.cu.withSourceFile { (sf, pc) =>
      val spc = new SearchPresentationCompiler(pc)
      for {
        comparator <- spc.comparator(entity.location) onEmpty reporter.reportError(comparatorErrMsg(entity.location, sf))
        names      <- spc.possibleNamesOfEntityAt(entity.location) onEmpty reporter.reportError(symbolErrMsg(entity.location, sf))
      } {
        val (occurrences, failures) = index.findOccurrences(names, relevantProjects(entity.location))
        failures.foreach(errorHandler)
        monitor.beginTask("Typechecking for exact matches", occurrences.size)
        processSame(occurrences, monitor, comparator, handler)
        monitor.done()
      }
    }(reporter.reportError(s"Could not access source file ${entity.location.cu.file.path}"))
  }

  // Loop through 'occurrences' and use the 'comparator' to find the
  // exact matches. Pass the results along to 'handler'.
  // The 'monitor' is needed to make it possible to cancel it and
  // report progress.
  private def processSame(
      occurrences: Seq[Occurrence],
      monitor: IProgressMonitor,
      comparator: SymbolComparator,
      handler: Confidence[Hit] => Unit): Unit = {

    for { occurrence <- occurrences if !monitor.isCanceled } {
      monitor.subTask(s"Checking ${occurrence.file.file.name}")
      val loc = Location(occurrence.file, occurrence.offset)
      comparator.isSameAs(loc) match {
        case Same         => handler(Certain(occurrence.toHit))
        case PossiblySame => handler(Uncertain(occurrence.toHit))
        case NotSame      => logger.debug(s"$occurrence wasn't the same.")
      }
      monitor.worked(1)
    }
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
