package org.scala.tools.eclipse.search
package searching

import scala.reflect.internal.util.OffsetPosition
import scala.reflect.internal.util.RangePosition
import scala.reflect.internal.util.SourceFile
import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.logging.HasLogger
import scala.tools.nsc.interactive.Response

import org.scala.tools.eclipse.search.Entity
import org.scala.tools.eclipse.search.ErrorHandlingOption
import org.scala.tools.eclipse.search.TypeEntity
import org.scala.tools.eclipse.search.indexing.Declaration
import org.scala.tools.eclipse.search.indexing.Occurrence

/**
 * Encapsulates PC logic. Makes it easier to control where the compiler data
 * structures are used and thus make sure that we conform to the synchronization
 * policy of the presentation compiler.
 */
class SearchPresentationCompiler(val pc: ScalaPresentationCompiler) extends HasLogger {

  // ADT used to express the result of symbolAt.
  protected sealed abstract class SymbolRequest
  protected case class FoundSymbol(symbol: pc.Symbol) extends SymbolRequest
  protected case object MissingSymbol extends SymbolRequest
  protected case object NotTypeable extends SymbolRequest

  /**
   * Given a `Location` return a Entity representation of
   * whatever Scala entity is being used or declared at the
   * given position.
   *
   * This returns None if the location can't be type-checked
   * or the file can't be access by the compiler.
   */
  def entityAt(loc: Location): Option[Entity] = {
    symbolAt(loc) match {
      case FoundSymbol(symbol) => pc.askOption { () =>
        val nme = getName(symbol)
        if      (symbol.isTrait) Trait(nme, loc)
        else if (symbol.isClass) Class(nme, loc)
        else if (symbol.isModule) Module(nme, loc)
        else if (symbol.isType) Type(nme, loc)
        else if (symbol.isMethod) Method(nme, loc)
        else if (symbol.isVal) Val(nme, loc)
        else if (symbol.isVar) Var(nme, loc)
        else new UnknownEntity(nme, loc)
      }
      case _ => None
    }
  }

  /**
   * Given two TypeEntities, check if `subType` is a subtype of `superType`.
   */
  def isSubtype(superType: TypeEntity, subType: TypeEntity): Boolean = {

    def getSymbol(req: SymbolRequest): Option[pc.Symbol] = req match {
      case FoundSymbol(sym) => Some(sym)
      case _ => None
    }

    (for {
      s1 <- getSymbol(symbolAt(superType.location)) onEmpty logger.debug(s"Couldn't get symbol at ${superType.location}")
      s2 <- getSymbol(symbolAt(subType.location)) onEmpty logger.debug(s"Couldn't get symbol at ${subType.location}")
      isSubtype <- pc.askOption { () =>
        !(s1.typeOfThis =:= s2.typeOfThis) && s2.typeOfThis <:< s1.typeOfThis
      }
    } yield isSubtype) getOrElse false
  }

  /**
   * In case the symbol is a var/val where the name can be either the
   * local name, the setter or the getter the getter name will
   * be used.
   */
  private def getName(symbol: pc.Symbol): String = {
    if(pc.nme.isSetterName(symbol.name)) {
      pc.nme.setterToGetter(symbol.name.toTermName).decodedName.toString
    } else if (pc.nme.isLocalName(symbol.name)) {
      pc.nme.localToGetter(symbol.name.toTermName).decodedName.toString
    } else {
      symbol.decodedName
    }
  }

  /**
   * Used to check if the entity at the given entity is something we
   * can find occurrences of. This is useful until we support all kinds
   * of entities.
   */
  def canFindReferences(entity: Entity): Boolean = {
    symbolAt(entity.location) match {
      case FoundSymbol(symbol) => pc.askOption { () =>
        symbol.isVal ||
        symbol.isMethod ||
        symbol.isConstructor ||
        symbol.isVar
      }.getOrElse(false)
      case _ => false
    }
  }

  /**
   * The name of the symbol at the given location and all the other
   * valid names for that symbol. For example Foo.apply() and Foo() are
   * both valid names for an invocation of Foo.apply
   */
  def possibleNamesOfEntityAt(loc: Location): Option[List[String]] = {

    def namesForValOrVars(symbol: pc.Symbol) = {
      val (setterName, getterName) = {
        if (pc.nme.isSetterName(symbol.name)) {
          (symbol.decodedName, pc.nme.setterToGetter(symbol.name.toTermName).decodedName)
        } else {
          val getter = if(pc.nme.isLocalName(symbol.name)) pc.nme.localToGetter(symbol.name.toTermName)
                       else symbol.name
          (pc.nme.getterToSetter(getter.toTermName).decodedName, getter.decodedName)
        }
      }

      List(setterName.toString, getterName.toString)
    }

    def namesForApply(symbol: pc.Symbol) = {
      List(symbol.decodedName.toString, symbol.owner.decodedName.toString)
    }

    def names(symbol: pc.Symbol) = pc.askOption { () =>
      if (isValOrVar(symbol)) namesForValOrVars(symbol)
      else if (symbol.nameString == "apply") namesForApply(symbol)
      else List(symbol.decodedName.toString)
    }

    symbolAt(loc) match {
      case FoundSymbol(symbol) => names(symbol)
      case _ => None
    }
  }

  /**
   * Given a type entity of a class declaration, return a sequence containing all of the
   * name of the super-types as well as instances of SymbolComparator for each super type
   * that can be used to find the declaration.
   */
  def superTypesOf(entity: TypeEntity): Option[Seq[(String, SymbolComparator)]] = {

    def superTypes(sym: pc.Symbol): Option[Seq[(String, SymbolComparator)]] = {

      // Needs to be invoked inside of the PC thread
      def toTpeRslt(tpe: pc.Type) = {
        // What we have here is concrete types, i.e. we have a declaration like
        // trait StringFoo extends Foo[String] - we have the Foo[String] type
        //
        // so we want to show the Foo[String] as a super-type, but when we need
        // to compare this type with the type of the declartion of Foo[A] we need
        // to use the generic type.
        (tpe.toString, concreteTypeComparator(tpe.typeSymbol.typeOfThis))
      }

      def symFilter(s: pc.Symbol) = s != null && s != pc.NoSymbol

      pc.askOption { () =>
        sym.parentSymbols filter(symFilter) map (s => toTpeRslt(s.typeOfThis))
      }
    }

    symbolAt(entity.location) match {
      case FoundSymbol(sym) => superTypes(sym)
      case _ => None
    }
  }

  /**
   * Given a location, find the declaration that contains the
   * given location. Consider the example below
   *
   *   class A extends Foo with Bar
   *
   * If the location is Foo it will return the location of A.
   */
  def declarationContaining(loc: Location): Option[Occurrence] = {

    loc.cu.withSourceFile { (sf,locPc) =>

      import locPc._

      class ModuleDefOrClassDefLocator(pos: Position) extends Locator(pos) {
        override def isEligible(t: Tree) =
          (t.isInstanceOf[ModuleDef] || t.isInstanceOf[ClassDef]) && super.isEligible(t)
      }

      def matches(name: Name, t: Tree) = Occurrence(
        name.decoded.toString,
        loc.cu.asInstanceOf[ScalaCompilationUnit],
        t.pos.point,
        Declaration,
        t.pos.lineContent,
        isInSuperPosition = false)

      locPc.withParseTree(sf) { parsed =>
        val pos = new OffsetPosition(sf, loc.offset)
        new ModuleDefOrClassDefLocator(pos).locateIn(parsed) match {
          case t @ ModuleDef(_, name, Template(supers, _, body)) => Some(matches(name, t))
          case t @ ClassDef(_, name, _, Template(supers, ValDef(_,_,selfType,_), body)) => Some(matches(name, t))
          case _ => None
        }
      }
    }(None)
  }

  /**
   * Get a comparator for a symbol at a given Location. The Comparator can be
   * used to see if symbols at other locations are the same as this symbol.
   */
  def comparator(loc: Location): Option[SymbolComparator] = {
    symbolAt(loc) match {
      case FoundSymbol(symbol) => Some(createSymbolComparator(symbol))
      case _ => None
    }
  }

  def subtypeComparator(loc: Location): Option[SymbolComparator] = {
    // Figure out if we have a generic or concrete type, i.e.
    // Option[A] vs. Option[String]
    // Needs to be invoked inside of the PC thread.
    def isGeneric(tpe: pc.Type) =
      tpe =:= tpe.typeSymbol.typeOfThis

    for {
      tpe <- typeAt(loc)
      rslt <- pc.askOption { () =>
        if (isGeneric(tpe)) genericTypeComparator(tpe)
        else concreteTypeComparator(tpe)
      }
    } yield rslt
  }

  /**
   * Comparator that can be used to compare symbols.
   */
  private def createSymbolComparator(symbol: pc.Symbol) = SymbolComparator { otherLoc =>
    def compare(s1: pc.Symbol, s2: pc.Symbol): Option[Boolean] = pc.askOption { () =>
      if (s1.isLocal || s2.isLocal) isSameSymbol(s1,s2)
      else if (isValOrVar(s1)) isSameValOrVar(s1, s2)
      else if (s1.isMethod) isSameMethod(s1.asMethod, s2)
      else isSameSymbol(s1, s2)
    } flatten

    otherLoc.cu.withSourceFile { (_, otherPc) =>
      val otherSpc = new SearchPresentationCompiler(otherPc)
      otherSpc.symbolAt(otherLoc) match {
        case otherSpc.FoundSymbol(symbol2) => (for {
          imported <- importSymbol(otherSpc)(symbol2)
          isSame   <- compare(symbol,imported)
          result   = if(isSame) Same else NotSame
        } yield result) getOrElse NotSame
        case _ => PossiblySame
      }
    }(PossiblySame)
  }

  /**
   * Comparator that can be used to compare types rather than symbols
   */
  private def concreteTypeComparator(tpe: pc.Type): SymbolComparator = {
    createTypeComparator(tpe) { (otherType: pc.Type) =>
      logger.debug(s"Concrete comparision of ${tpe} and the other ${otherType}")
      otherType =:= tpe
    }
  }

  private def genericTypeComparator(tpe: pc.Type): SymbolComparator = {
    // The type we have is generic, thus we want to compare the type
    // with the generic type of any concrete type instantiations
    createTypeComparator(tpe) { (otherType: pc.Type) =>
      logger.debug(s"Generic comparision of ${tpe} and the other ${otherType.typeSymbol.typeOfThis}")
      tpe =:= otherType.typeSymbol.typeOfThis
    }
  }

  private def createTypeComparator(tpe: pc.Type)(f: pc.Type => Boolean): SymbolComparator = {
    SymbolComparator { locationOfOtherType =>
      locationOfOtherType.cu.withSourceFile { (_, pc) =>
        val spc = new SearchPresentationCompiler(pc)
        spc.symbolAt(locationOfOtherType) match {
          case spc.FoundSymbol(otherSymbol) => (for {
            importedSymbol <- importSymbol(spc)(otherSymbol)
            result <- pc.askOption { () => if (f(importedSymbol.tpe)) Same else NotSame }
          } yield result) getOrElse NotSame
          case _ => PossiblySame
        }
      }(PossiblySame)
    }
  }

  /**
   * Get the symbol of the entity at a given location
   *
   * The source file needs not be loaded in the presentation compiler prior to
   * this call.
   *
   * This resolves overloaded method symbols. @See resolveOverloadedSymbol for
   * more information.
   */
  protected def symbolAt(loc: Location): SymbolRequest = {
    loc.cu.withSourceFile { (sf, _) =>
      val typed = new Response[pc.Tree]
      val pos = new OffsetPosition(sf, loc.offset)
      pc.askTypeAt(pos, typed)
      typed.get.fold(
        tree => {
          (for {
            (overloaded, treePos) <- pc.askOption { () => (tree.symbol.isOverloaded, tree.pos) }
            if overloaded
          } yield {
            resolveOverloadedSymbol(treePos, sf)
          }).getOrElse(filterNoSymbols(tree.symbol))
        },
        err => {
          logger.debug(err)
          NotTypeable
        })
    }(NotTypeable)
  }

  protected def typeAt(loc: Location): Option[pc.Type] = {
    // Again we have the lovely problem that the compiler
    // will evaluate as little information as possible so
    // if we ask for the type at (| is the location)
    //
    //  class StringFoo extends |Foo[String]
    //
    // Then tree.tpe will be Foo and not Foo[String].

    // This takes care of getting the full type in case it's
    // a type that takes type arguments
    def getTheFullType(treePos: pc.Position, cu: SourceFile): Option[pc.Type] = {
      val TYPE_PARAMS_END_CHAR = 1

      class AppliedTypeTreeLocator(pos: pc.Position) extends pc.Locator(pos) {
        override def isEligible(t: pc.Tree) = t.isInstanceOf[pc.AppliedTypeTree] && super.isEligible(t)
      }

      val posOpt = pc.withParseTree(cu) { parsed =>
        new AppliedTypeTreeLocator(treePos.pos).locateIn(parsed) match {
          case pc.AppliedTypeTree(_, types) => {
            val max = types.map(_.pos.endOrPoint).max
            val start = treePos.pos.point
            val end = max + TYPE_PARAMS_END_CHAR
            val pos = new RangePosition(cu, start, start, end)
            logger.debug("pos is " + pos)
            Some(pos)
          }
          case x => None
        }
      }

      posOpt flatMap { pos =>
        val typed = new Response[pc.Tree]
        pc.askTypeAt(pos, typed)
        typed.get.fold(
          tree => {
            logger.debug("Found the type " + tree.tpe)
            logger.debug("++ in the tree " + tree)
            Some(tree.tpe)
          },
          err => {
            logger.debug(err)
            None
          })
      }
    }

    loc.cu.withSourceFile { (sf, _) =>
      val typed = new Response[pc.Tree]
      val pos = new OffsetPosition(sf, loc.offset)

      pc.askTypeAt(pos, typed)
      typed.get.fold(
        tree => pc.askOption { () =>
          if (tree.isDef) {
            logger.debug("Was a def type")
            Some(tree.symbol.tpe) // it's a definition so we can just use the type.
          }
          else if (tree.symbol.typeSignature.takesTypeArgs) {
            logger.debug("Generic type so expanding selection")
            getTheFullType(tree.pos, sf) // It's a type instantiation of a generic type
          }
          else {
            logger.debug("Normal concrete type")
            Some(tree.tpe) // A type is referenced and it's not generic so we're good.
          }
        },
        err => {
          logger.debug(err)
          None
        }).flatten
    }(None)
  }

  /**
   * If the symbol at the given location is known to be an overloaded symbol
   * this will resolve the overloaded symbol by asking the compiler to type-check
   * a bit more of the tree at the given location.
   *
   * This is needed because `askTypeAt` will only return the smallest fully attributed
   * tree that encloses position. Consider the following example
   *
   *   def askOption[A](op: () => A): Option[A] = askOption(op, 10000)
   *   def askOption[A](op: () => A, timeout: Int): Option[A] = None
   *
   *   askOption { () =>
   *     ...
   *   }
   *
   *  If the position the beginning of the `askOption` identifier (the invocation) then
   *  the presentation compiler will only type-check the receiver (the Select node) and
   *  not the arguments and thus will not resolve the overloaded method symbol.
   *
   *  We fix this by asking the compiler to type-check more of the tree using a
   *  RangePosition.
   *
   */
  private def resolveOverloadedSymbol[A](treePos: pc.Position, cu: SourceFile): SymbolRequest = {

    // There are two cases we need to consider then figuring out how
    // far the RangePosition should reach.
    //
    // 1. It is a normal Apply.
    //
    //    In this case we simply extend the RangePosition 1 char past
    //    the identifier, i.e.
    //
    //      {foo(}x)
    //
    //    where {} represents the RangePosition.
    //
    // 2. It's a TypeApply, i.e. a parametric method where the type
    //    parameters have been supplied explicitly.
    //
    //    In this case we have to extend the RangePosition 2 chars
    //    past the end of the last type parameter,i.e.
    //
    //      {foo[String,Int](}
    //

    val EXTRA_SPACE = 1
    val TYPE_PARAMS_END_CHAR = 1

    val posInCaseOfTypeApply = pc.withParseTree(cu) { parsed =>
      import pc._
      // We want to find the TypeApply that contains the Select
      // node.
      class TypeApplyLocator(pos: Position) extends Locator(pos) {
        override def isEligible(t: Tree) = t.isInstanceOf[TypeApply] && super.isEligible(t)
      }

      new TypeApplyLocator(treePos.pos).locateIn(parsed) match {
        case TypeApply(_, types) => {
          val max = types.map(_.pos.endOrPoint).max
          Some((treePos.pos.point, max + EXTRA_SPACE + TYPE_PARAMS_END_CHAR))
        }
        case x => None
      }
    }

    lazy val posInCaseOfApply = (
        treePos.pos.point,
        treePos.pos.endOrPoint + EXTRA_SPACE)

    val (start, end) = posInCaseOfTypeApply.getOrElse(posInCaseOfApply)

    val typed = new Response[pc.Tree]
    val pos = new RangePosition(cu, start, start, end)
    pc.askTypeAt(pos, typed)
    typed.get.fold(
      tree => {
        filterNoSymbols(tree.symbol)
      },
      err => {
        logger.debug(err)
        NotTypeable
      })
  }

  private def filterNoSymbols(sym: pc.Symbol): SymbolRequest = {
    if (sym == null) {
      MissingSymbol // Symbol is null if there isn't any node at the given location (I think!)
    } else if (sym == pc.NoSymbol) {
      NotTypeable // NoSymbol if we can't typecheck the given node.
    } else {
      FoundSymbol(sym)
    }
  }

  /**
   * Import a symbol from one presentation compiler into another. This is required
   * before you can compare two symbols originating from different presentation
   * compiler instance.
   *
   * @note it should always be called within the Presentation Compiler Thread.
   *
   * TODO: Is the imported symbol discarded again, or does this create a memory leak?
   *       Ticket #1001703
   */
  private def importSymbol(spc: SearchPresentationCompiler)(s: spc.pc.Symbol): Option[pc.Symbol] = {

    // https://github.com/scala/scala/blob/master/src/reflect/scala/reflect/api/Importers.scala
    val importer0 = pc.mkImporter(spc.pc)
    val importer = importer0.asInstanceOf[pc.Importer { val from: spc.pc.type }]

    // Before importing the symbol, we have to make sure that it is fully initialized,
    // otherwise it would access thread unsafe members in spc.pc when importing the symbol
    // into pc.
    spc.pc.askOption { () =>
      s.initialize
      s.ownerChain.foreach(_.initialize)
    } onEmpty (logger.debug("Timed out on initializing symbol before import"))

    pc.askOption { () =>
      importer.importSymbol(s)
    }  onEmpty (logger.debug("Timed out on symbol import"))

  }

  /**
   * Check if `s2` is valid reference to the symbol var/val `s1`.
   *
   * Given that we merge overridden members we need to consider
   * that a def may also be a valid reference to a var/val since a
   * val can override a method and a method can override an abstract
   * var.
   *
   * @note it should always be called within the Presentation Compiler Thread.
   */
  private def isSameValOrVar(s1: pc.Symbol, s2: pc.Symbol): Option[Boolean] = {
    /*
     * S1: Is the getter/setter or the underlying symbol of a var/val.
     * S2: Same as S1 or a method.
     */
    if (isValOrVar(s2)) {
      isSameSymbol(s1.getter, s2.getter)
    } else if (s2.isMethod) {
      if (pc.nme.isSetterName(s2.name)) isSameSymbol(s1.setter, s2)
      else isSameSymbol(s1.getter, s2)
    } else None
  }

  /**
   * Check if `s2` is a valid reference to the method `s1`.
   *
   * Given that we merge overridden members we need to consider
   *
   *   1. A 0-arg method can be overridden by a val
   *   2. A method can override an abstract var (both getter and setter)
   *
   * So S2 can either a method, a val/var or their underlying symbols.
   *
   * @note it should always be called within the Presentation Compiler Thread.
   *
   */
  private def isSameMethod(s1: pc.MethodSymbol, s2: pc.Symbol): Option[Boolean] = {
    // S1: Is a method, and not a getter/setter of a field.
    // S2: Can be a method, a val/var or their underlying symbols
    if (isValOrVar(s2)) {
      if (pc.nme.isSetterName(s1.name)) isSameSymbol(s1, s2.setter)
      else isSameSymbol(s1, s2.getter)
    } else if (s2.isMethod) {
      isSameSymbol(s1, s2)
    } else None
  }

  /**
   * Check if symbols `s1` and `s2` are describing the same symbol. We consider two
   * symbols to be the same if
   *
   *  - They have the same name
   *  - s1.owner and s2.owner are in the same hierarchy
   *  - They have the same type signature or one is overriding the other
   *
   * Now if the symbols have the same name and are in the same hierarchy but the
   * type-signature doesn't match we check if one symbol is overriding the other.
   * The reason for this is that the signature might contain references to this.type.
   *
   * In the case of comparing vals or vars the caller of this method has to make
   * sure the the appropriate symbols are compared, e.g. that both S1 and S2
   * are setters.
   */
  private def isSameSymbol(s1: pc.Symbol, s2: pc.Symbol): Option[Boolean] = {
    pc.askOption { () =>

      lazy val isInHiearchy = s1.owner.isSubClass(s2.owner) ||
                              s2.owner.isSubClass(s1.owner)

      lazy val hasSameName = s1.name == s2.name

      lazy val hasSameTypeSignature = s1.typeSignature =:= s2.typeSignature

      // If s1 and s2 are defined in the same class the owner will be the same
      // thus s1.overriddenSymbol(S2.owner) will actually return s1.
      lazy val isOverridden = s1.owner != s2.owner &&
                             (s1.overriddenSymbol(s2.owner) == s2 ||
                              s2.overriddenSymbol(s1.owner) == s1)

      (s1 == s2) || // Fast-path.
      hasSameName &&
      isInHiearchy &&
      (hasSameTypeSignature || isOverridden)
    } onEmpty logger.debug("Timed out when comparing symbols")
  }

  private def isValOrVar(s: pc.Symbol): Boolean = {
    s.isVar || s.isVal || s.isSetter || s.isGetter
  }
}
