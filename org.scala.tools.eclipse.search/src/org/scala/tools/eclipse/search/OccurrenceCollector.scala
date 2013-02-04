package org.scala.tools.eclipse.search

import org.eclipse.core.resources.IFile
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.eclipse.logging.HasLogger

/**
 * Used to parse and traverse the parse trees of a compilation unit finding
 * all the occurrence of Scala entities we're interested in.
 */
object OccurrenceCollector extends HasLogger {

  def findOccurrences(file: ScalaSourceFile): Either[String, Seq[Occurrence]] = {
    file.withSourceFile( (source, pcompiler) => {
      pcompiler.withParseTree(source) { tree =>
        Right(findOccurrences(pcompiler)(file, tree)): Either[String, Seq[Occurrence]]
      }
    })(Left("Couldn't get source file for %".format(file.file.path.toString())))
  }

  private def findOccurrences(pc: ScalaPresentationCompiler)
                             (file: ScalaSourceFile, tree: pc.Tree): Seq[Occurrence] = {
    import pc._
    val fileName = file.file.file.getName()
    val path = file.file.file.getAbsolutePath()

    val occurrences = new scala.collection.mutable.ListBuffer[Occurrence]()
    val traverser = new Traverser {
      override def traverse(tree: Tree) {

        tree match {
          // Direct invocations of methods
          case Apply(t@Ident(fun), args) if !isSynthetic(pc)(t, fun.toString) =>
            val (line, column) = positionInfo(pc)(t)
            occurrences += Occurrence(fun.toString, path, fileName, line, column, Reference, Method)
            args.foreach { super.traverse } // recurse on the arguments

          // E.g. foo.bar()
          case Apply(t@Select(rest, name), args) if !isSynthetic(pc)(t, name.toString) =>
            val (line, column) = positionInfo(pc)(t)
            occurrences += Occurrence(name.toString, path, fileName, line, column, Reference, Method)
            args.foreach { super.traverse } // recurse on the arguments
            super.traverse(rest) // We recurse in the case of chained invocations, foo.bar().baz()

          // Invoking a method w/o an argument doesn't result in apply, just an Ident node.
          case t@Ident(fun) if !isSynthetic(pc)(t, fun.toString) =>
            val (line, column) = positionInfo(pc)(t)
            occurrences += Occurrence(fun.toString, path, fileName, line, column, Reference, Method) /* Not necessarily a method. */

          // Invoking a method on an instance w/o an argument doesn't result in an Apply node, simply a Select node.
          case t@Select(rest,name) if !isSynthetic(pc)(t, name.toString) =>
            val (line, column) = positionInfo(pc)(t)
            occurrences += Occurrence(name.toString, path, fileName, line, column, Reference, Method) /* Not necessarily a method. */
            rest.foreach { super.traverse } // recurse in the case of chained selects: foo.baz.bar

          // Method definitions
          case t@DefDef(_, name, _, _, _, body) if !isSynthetic(pc)(t, name.toString) =>
            val (line, column) = positionInfo(pc)(t)
            occurrences += Occurrence(name.toString, path, fileName, line, column, Declaration, Method)
            super.traverse(body) // We recurse in the case of chained invocations, foo.bar().baz()

          case _ => super.traverse(tree)
        }
      }
    }
    traverser.apply(tree)
    occurrences
  }

  private def positionInfo(pc:ScalaPresentationCompiler)(tree: pc.Tree): (Int, Int) = {
    if (tree.pos != pc.NoPosition) {
      var line = 0
      var column = 0
      try {
        line = tree.pos.line
        column = tree.pos.column
        (line, column)
      } catch {
        case e: Exception =>
          logger.debug(e)
          (line, column)
      }
    } else (0,0)
  }

  private def isSynthetic(pc: ScalaPresentationCompiler)
                         (tree: pc.Tree, name: String): Boolean = {
    val syntheticNames = Set("<init>")
    tree.pos == pc.NoPosition || syntheticNames.contains(name)
  }

}