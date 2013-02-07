package org.scala.tools.eclipse.search

import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.eclipse.logging.HasLogger

/**
 * Our own abstraction over symbols.
 *
 * We need this because the compiler symbols don't implement equals
 * and hence will be compared using reference identity. This is a
 * problem for two reasons
 *
 * 1. Symbols are computed after each type-check, and since the presentation
 *    compiler can be asked to type-check a file multiple times new symbols
 *    for the same entities will eventually be created.
 * 2. Each project has it’s own presentation compiler, so comparing symbols
 *    between projects won’t work.
 *
 * Additionally this means we can decouple some of our code from the compiler,
 * which is always nice.
 */
object SymbolAbstraction extends HasLogger {

  type Type = String // TODO: Figure out how to deal with types.

  sealed abstract trait Sym {
    val name: String
  } 

  case class ModuleSym(
      val name: String) extends Sym

  case class ClassSym(
      val name: String) extends Sym

  case class MethodSym(
      val name: String,
      val args: Seq[Seq[Argument]], /* many parameter lists */
      val returnType: Type,
      val owner: Sym                /* class or module symbol */
  ) extends Sym

  case class Argument(
      val name: String,
      val typ: Type
  )

  /**
   * Converts a Scala compiler symbol to our own symbol abstraction.
   */
  def fromSymbol(pc: ScalaPresentationCompiler)(sym: pc.Symbol): Option[Sym] = {
    import pc._

    def fromMethodSymbol(sym: MethodSymbol): Option[Sym] = {
      val arguments = ask { () => sym.paramss.map { (syms: List[Symbol]) =>
        syms.map { s => Argument(s.nameString, s.tpe.toLongString) }
      }}
      val (name, rtpe) = ask { () =>
        (sym.nameString, sym.returnType.toLongString)
      }
      val ownerSymbol = {
        ask { () => sym.owner } 
      }
      val owner = fromSymbol(pc)(ownerSymbol)
      logger.debug("converting MethodSymbol %s".format(name))
      owner.map { ow => 
        MethodSym(name, arguments, rtpe, ow)
      } ifEmpty {
        logger.debug("Couldn't convert owner %s".format(ownerSymbol))
      }
    }

    def fromTermSymbol(sym: TermSymbol): Option[Sym] = {
      fromSymbol(pc)(ask { () => sym.referenced })
    }

    def fromModuleSymbol(sym: ModuleSymbol): Option[Sym] = {
      val name = ask { () => sym.nameString }
      logger.debug("converting ModuleSymbol %s".format(name))
      Some(ModuleSym(name))
    }

    def fromClassSymbol(sym: ClassSymbol): Option[Sym] = {
      val name = ask { () => sym.nameString }
      logger.debug("converting ClassSymbol %s".format(name))
      Some(ClassSym(name))
    }

    def fromModuleClassSymbol(sym: ModuleClassSymbol): Option[Sym] = {
      val name = ask { () => sym.nameString }
      logger.debug("converting ModuleClassSymbol %s".format(name))
      Some(ModuleSym(name))
    }

    sym match {
      case sym: MethodSymbol => fromMethodSymbol(sym)
      case sym: ModuleSymbol => fromModuleSymbol(sym)

      case sym: FreeTermSymbol =>
        logger.debug("Found a FreeTermSymbol")
        None

      case sym: FreeTypeSymbol =>
        logger.debug("Found a FreeTypeSymbol")
        None

      case sym: TermSymbol => fromTermSymbol(sym)

      case sym: TypeSkolem =>
        logger.debug("Found a TypeSkolem ")
        None

      case sym: ModuleClassSymbol => fromModuleClassSymbol(sym)

      case sym: ClassSymbol => fromClassSymbol(sym)

     case sym: TypeSymbol =>
        logger.debug("Found a TypeSymbol")
        None

      case sym => {
        logger.debug("Found a symbol type that I couldn't convert " + sym)
        None
      }
    }
  }

}