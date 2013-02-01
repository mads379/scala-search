package org.scala.tools.eclipse.search

import org.eclipse.core.resources.IFile
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.ScalaPresentationCompiler

/**
 * Used to parse and traverse the parse trees of a compilation unit finding
 * all the occurrence of Scala entities we're interested in.
 */
object OccurrenceCollector {

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
    val path = file.file.file.getAbsolutePath()
    tree.collect {
      // Direct invocations of methods
      case Apply(t@Ident(fun), _) if !isSynthetic(pc)(t, fun.toString) =>
        Occurrence(fun.toString, path, t.pos.line, t.pos.column, Reference, Method)

      // You can have a long chain of invocations Apply(Select(..))
      // TODO check if this will skip some occurrences, i.e. my.foo().bar().test() might skip bar, test.
      case Apply(t@Select(_, name), _) if !isSynthetic(pc)(t, name.toString) =>
        Occurrence(name.toString, path, t.pos.line, t.pos.column, Reference, Method)

      // A method w/o an argument doesn't result in an Apply node, simply a Select node.
      case t@Select(_,name) if !isSynthetic(pc)(t, name.toString) =>
        Occurrence(name.toString, path, t.pos.line, t.pos.column, Reference, Method) /* Not necessarily a method. */

      // Method definitions
      case t@DefDef(_, name, _, _, _, _) if !isSynthetic(pc)(t, name.toString) =>
        Occurrence(name.toString, path, t.pos.line, t.pos.column, Declaration, Method)
    }
  }

  private def isSynthetic(pc: ScalaPresentationCompiler)
                         (tree: pc.Tree, name: String): Boolean = {
    val syntheticNames = Set("<init>")
    tree.pos == pc.NoPosition || syntheticNames.contains(name)
  }

}