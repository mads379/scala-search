package org.scala.tools.eclipse.search.indexing

import scala.tools.nsc.CompilerCommand
import scala.tools.nsc.Settings
import scala.tools.nsc.reporters.StoreReporter
import scala.tools.nsc.Global
import java.io.File
import org.scala.tools.eclipse.search.using
import junit.framework.Assert

/**
 * Used to write test code that uses the Scala compiler
 * to compile Scala files (or strings) on the fly.
 */
trait SourceCompiler {

  def root: File

  // Shared instances for each call to `compile`.
  private val reporter = new StoreReporter()
  private val settings = new Settings(println(_))
  settings.outdir.value = root.getAbsolutePath
  private val compiler = new Global(settings, reporter)

  /**
   * Compiles the given Scala string and returns a File
   * that points to the folder that contains the bytecode.
   *
   * We return a folder rather than the file since Scala code
   * will often generate many classfiles.
   */
  def compile(name: String)(str: String): Unit = {

    val file = new File(root, name)

    using(new java.io.PrintWriter(file)) { writer =>
      writer.write(str)
      writer.flush()
    }

    val arguments = List(
      "-bootclasspath", "/usr/local/Cellar/scala/2.10.2/libexec/lib/scala-library.jar",
      "-classpath", "/usr/local/Cellar/scala/2.10.2/libexec/lib/scala-compiler.jar"
    )

    val command = new CompilerCommand(file.getAbsolutePath :: arguments, settings)
    val run = new compiler.Run()
    run compile command.files

    if (reporter.hasErrors) {
      reporter.infos foreach { i => Assert.fail(i.toString) }
      reporter.reset()
    }
  }

}