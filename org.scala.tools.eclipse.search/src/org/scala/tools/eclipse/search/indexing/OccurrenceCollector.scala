package org.scala.tools.eclipse.search.indexing

import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.logging.HasLogger
import scala.util._
import scala.tools.eclipse.javaelements.ScalaCompilationUnit

/**
 * Used to parse and traverse the parse tree of a compilation unit finding
 * all the occurrence of Scala entities we're interested in.
 */
object OccurrenceCollector extends HasLogger {

  class InvalidPresentationCompilerException(msg: String) extends Exception(msg)

  /**
   * Find all occurrences of words we're find interesting in a compilation unit.
   *
   * This can fail in the following ways
   *
   * InvalidPresentationCompilerException:
   *   if the presentation compiler is not available (for instance, if it cannot
   *   be started because of classpath issues)
   *
   */
  def findOccurrences(file: ScalaCompilationUnit): Try[Seq[Occurrence]] = {

    lazy val err: Try[Seq[Occurrence]] = Failure(
        new InvalidPresentationCompilerException(
            s"Couldn't get source file for ${file.workspaceFile.getProjectRelativePath()}"))

    file.withSourceFile( (source, pcompiler) => {
      pcompiler.withParseTree(source) { tree =>
        Success(findOccurrences(pcompiler)(file, tree)): Try[Seq[Occurrence]]
      }
    })(err)

  }

  private def findOccurrences(pc: ScalaPresentationCompiler)
                             (file: ScalaCompilationUnit, tree: pc.Tree): Seq[Occurrence] = {
    import pc._

    val occurrences = new scala.collection.mutable.ListBuffer[Occurrence]()
    var isSuper = false

    val traverser = new Traverser {
      override def traverse(t: Tree) {

        // Avoid passing the same arguments all over.
        val Occ = Occurrence(_: String, file, t.pos.point, _: OccurrenceKind, t.pos.lineContent, isSuper)

        t match {

          case Ident(name) if !isSynthetic(pc)(t) =>
            occurrences += Occ(name.decodedName.toString, Reference)

          case Select(rest,name) if !isSynthetic(pc)(t) =>
            occurrences += Occ(name.decodedName.toString, Reference)
            traverse(rest) // recurse in the case of chained selects: foo.baz.bar

          // Method definitions
          case DefDef(mods, name, _, args, _, body) if !isSynthetic(pc)(t) =>
            occurrences += Occ(name.decodedName.toString, Declaration)
            traverseTrees(mods.annotations)
            traverseTreess(args)
            traverse(body)

          // Val's and arguments.
          case ValDef(_, name, tpt, rhs) =>
            occurrences += Occ(name.decodedName.toString, Declaration)
            traverse(tpt)
            traverse(rhs)

          // Class and Trait definitions
          case ClassDef(_, name, _, Template(supers, ValDef(_,_,selfType,_), body)) =>
            occurrences += Occ(name.decodedName.toString, Declaration)
            isSuper = true
            traverseTrees(supers)
            traverse(selfType)
            isSuper = false
            traverseTrees(body)

          // Object definition
          case ModuleDef(_, name, Template(supers, _, body)) =>
            occurrences += Occ(name.decodedName.toString, Declaration)
            isSuper = true
            traverseTrees(supers)
            isSuper = false
            traverseTrees(body)

          case _ =>
            super.traverse(t)
        }
      }
    }
    traverser.apply(tree)
    occurrences.toList
  }

  private def isSynthetic(pc: ScalaPresentationCompiler)
                         (tree: pc.Tree): Boolean = {
    tree.pos == pc.NoPosition
  }

}