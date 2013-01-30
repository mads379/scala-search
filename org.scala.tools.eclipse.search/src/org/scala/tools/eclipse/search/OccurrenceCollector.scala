package org.scala.tools.eclipse.search

import org.eclipse.core.resources.IFile
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.ScalaPresentationCompiler

object OccurrenceCollector {

  def findOccurrences(file: ScalaSourceFile): Either[String, Seq[Occurrence]] = {
    file.withSourceFile( (source, pcompiler) => {
      pcompiler.withParseTree(source) { tree =>
        Right(findOccurrences(pcompiler)(tree)): Either[String, Seq[Occurrence]]
      }
    })(Left("Couldn't get source file for %".format(file.file.path.toString()))) 
  }

  private def findOccurrences(pc: ScalaPresentationCompiler)(tree: pc.Tree): Seq[Occurrence] = {
    import pc._
    tree.collect {
      case Apply(t@Ident(fun), _) if !isSynthetic(pc)(t, fun.toString) =>
        Occurrence(fun.toString, 0, 0, Reference, Method)
      case t@DefDef(_, name, _, _, _, _) if !isSynthetic(pc)(t, name.toString) =>
        Occurrence(name.toString, 0, 0, Declaration, Method)
    }
  }

  private def isSynthetic(pc: ScalaPresentationCompiler)(tree: pc.Tree, name: String): Boolean = {
    val syntheticNames = Set("<init>")
    tree.pos == pc.NoPosition || syntheticNames.contains(name)
  }

}