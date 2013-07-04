package org.scala.tools.eclipse.search.indexing

import java.io.File
import java.io.BufferedInputStream
import java.io.FileInputStream
import org.objectweb.asm.ClassReader
import org.objectweb.asm.commons.EmptyVisitor
import scala.tools.asm.Opcodes

sealed trait BytecodeDeclaration {
  val name: String
  val parents: Seq[String]
}
case class ClassDeclaration(name: String, parents: Seq[String]) extends BytecodeDeclaration
case class ObjectDeclaration(name: String, parents: Seq[String]) extends BytecodeDeclaration
case class TraitDeclaration(name: String, parents: Seq[String]) extends BytecodeDeclaration

/**
 * Component used to traverse a single classfile and
 * report the entities we're interested in.
 *
 * For now it only finds classes.
 */
object ClassfileTraverser {

  /**
   * Traverses bytecode and finds all declarations of types (Classes,
   * Objects, Traits)
   *
   * The file can be either a folder or a .class file. If it's folder
   * we recursivly search in all sub-folder.
   */
  def findDeclarations(file: File): Seq[BytecodeDeclaration] = {
    if (file.isDirectory()) {
      file.listFiles.toList map(findDeclarations(_)) flatten
    } else if (file.getName().endsWith(".class")){
      val is = new BufferedInputStream(new FileInputStream(file))
      val cr = new ClassReader(is)
      // The ASM API is event-based, we want it to be strict atm.
      var declaration: BytecodeDeclaration = null
      cr.accept(new CV( cd => declaration = cd), ClassReader.SKIP_CODE)
      List(declaration)
    } else Nil
  }

  private class CV(f: BytecodeDeclaration => Unit) extends EmptyVisitor {

    // Bytecode separates package name identifiers with '/'
    def demanglePackageName(name: String): String = name.replace("/",".")
    def demangleObjectName(name: String): String = name.dropRight(1) // Objects end with $

    override def visit(version: Int, access: Int, name: String, signature: String, superName: String, interfaces: Array[String]) {
      val superTypes = demanglePackageName(superName) :: interfaces.toList.map(demanglePackageName)
      val demangled = demanglePackageName(name)
      val declaration = {
        if ((access & Opcodes.ACC_INTERFACE) != 0) TraitDeclaration(demangled, superTypes)
        else if (name.takeRight(1) == "$") ObjectDeclaration(demangleObjectName(demangled), superTypes)
        else ClassDeclaration(demangled, superTypes )
      }
      f(declaration)
    }
  }

}

