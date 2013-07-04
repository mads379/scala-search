package org.scala.tools.eclipse.search.indexing

import java.io.File
import org.junit.Before
import org.junit.After
import org.junit.Assert._
import org.junit.Test
import org.scala.tools.eclipse.search.TestUtil

class ClassfileTraverserTest {

  import ClassfileTraverserTest._

  @Before
  def before: Unit = {
    root.mkdirs
  }

  @After
  def after: Unit = {
    if (!deleteAll(root))
      fail(s"Wasn't able to delete file: ${root.getAbsolutePath}")
  }

  /*----------------------------*
   * Classes
   *----------------------------*/

  @Test
  def findClasses_classes_canFindSimpleClass = compileDocument {"""
    class A
  """} expected List(ClassDeclaration("A", Seq("java.lang.Object")))

  @Test
  def findClasses_classes_understandsPackages = compileDocument {"""
    package a.b.c
    class A
  """} expected List(ClassDeclaration("a.b.c.A", Seq("java.lang.Object")))

  @Test
  def findClasses_classes_canFindManyClasses = compileDocument {"""
    class A
    class B
  """} expected List(ClassDeclaration("A", Seq("java.lang.Object")),
                     ClassDeclaration("B", Seq("java.lang.Object")))

  @Test
  def findClasses_classes_canHandleInheritance = compileDocument {"""
    package a
    class A
    class B extends A
  """} expected List(ClassDeclaration("a.A", Seq("java.lang.Object")),
                     ClassDeclaration("a.B", Seq("a.A")))

  @Test
  def findClasses_classes_canFindInnerClasses = compileDocument {"""
    class A {
      class B
    }
  """} expected List(ClassDeclaration("A$B", Seq("java.lang.Object")), //TOOD: Demangle
                     ClassDeclaration("A", Seq("java.lang.Object")))

  /*----------------------------*
   * Objects                    *
   *----------------------------*/

  @Test
  def findClasses_objects_canFindSimpleObjects = compileDocument {"""
    object A
  """} expected List(ObjectDeclaration("A", Seq("java.lang.Object")))

  @Test
  def findClasses_objects_canHandleInhertiance = compileDocument {"""
    class A
    object B extends A
  """} expected List(ClassDeclaration("A", Seq("java.lang.Object")),
                     ObjectDeclaration("B", Seq("A")))

  @Test
  def findClasses_objects_canHandleMixins = compileDocument {"""
    object A extends Object with Serializable
  """} expected List(ObjectDeclaration("A", Seq("java.lang.Object", "scala.Serializable")))

  @Test
  def findClasses_objects_canFindNestedObjects = compileDocument {"""
    object A {
      object B
    }
  """} expected List(ObjectDeclaration("A", Seq("java.lang.Object")),
                     ObjectDeclaration("A$B", Seq("java.lang.Object")))

  // TODO: Abstract class

  /*----------------------------*
   * Traits                     *
   *----------------------------*/

  @Test
  def findClasses_traits_canFindSimpleTrait = compileDocument {"""
    trait A
  """} expected List(TraitDeclaration("A", Seq("java.lang.Object")))

  @Test
  def findClasses_traits_canHandleInheritance = compileDocument {"""
    class A
    trait B
    trait C extends A with B
  """} expected List(ClassDeclaration("A", Seq("java.lang.Object")),
                     TraitDeclaration("B", Seq("A")))

  @Test
  def findClasses_traits_canHandleMixins = compileDocument {"""
    trait A extends Object with Serializable
  """} expected List(TraitDeclaration("A", Seq("java.lang.Object", "scala.Serializable")))

  @Test
  def findClasses_traits_canHandleSelfTypes = compileDocument {"""
    trait A
    trait B { this: A => }
  """} expected List(TraitDeclaration("A", Seq("java.lang.Object")),
                     TraitDeclaration("B", Seq("A")))
  @Test
  def findClasses_traits_canHandleSelfTypesWithMixins = compileDocument {"""
    trait A
    trait B { this: A with Serializable => }
  """} expected List(TraitDeclaration("A", Seq("java.lang.Object")),
                     TraitDeclaration("B", Seq("A","scala.Serializable")))

  @Test
  def findClasses_traits_canHandleTraitWithImplementation = compileDocument {"""
    trait A {
      def foo: String
      def bar = "test"
    }
  """} expected List(TraitDeclaration("A", Seq("java.lang.Object")))

}

object ClassfileTraverserTest extends TestUtil with SourceCompiler {

  override def root = {
    val f = new File(mkPath("target","ClassfileTraverserTest"))
    f.mkdirs
    f
  }

  protected trait BinaryTestDocument {
    def expected(xs: Seq[BytecodeDeclaration]): Unit
  }

  def compileDocument(text: String) = new BinaryTestDocument {
    def expected(xs: Seq[BytecodeDeclaration]): Unit = {
      compile("DoesntMatter.scala")(text)
      val classes = ClassfileTraverser.findDeclarations(root)
      assertEquals(xs.toList, classes)
    }
  }

}