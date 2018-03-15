package encrywm.frontend.semantics

import encrywm.builtins.Builtins
import encrywm.frontend.parser.Ast._

class SemanticAnalyzer extends TreeNodeVisitor {

  private var currentScopeOpt: Option[ScopedSymbolTable] = None

  override def visit(node: AST_NODE): Unit = node match {
    case tr: TREE_ROOT => visitRoot(tr)
    case stmt: STMT => visitStmt(stmt)
    case expr: EXPR => visitExpr(expr)
    case _ => // Do nothing.
  }

  private def visitRoot(node: TREE_ROOT): Unit = node match {
    case c: TREE_ROOT.Contract =>
      currentScopeOpt = Some(InitialScope.global)
      c.body.foreach(visit)
    case _: TREE_ROOT.Expression => // Do we need this branch for scripts debugging?
    case _ => // Do nothing.
  }

  private def visitStmt(node: STMT): Unit = node match {

    case asg: STMT.Assign => asg.target match {
        case decl: EXPR.Decl => visitDecl(decl)
        case _: EXPR.Name => // Reassignment. This subject is under discussion now.
        case _ => // Do nothing.
      }
      visit(asg.value)

    case fd: STMT.FunctionDef =>
      assertDefined(fd.returnType.name)
      val returnTypeSymbol = BuiltInTypeSymbol(fd.returnType.name)
      val paramSymbols = fd.args.args.map { arg =>
        arg.target match {
          case n: EXPR.Name =>
            val typeSymbolOpt = arg.typeOpt.map { t =>
              assertDefined(t.name)
              BuiltInTypeSymbol(t.name)
            }
            VariableSymbol(n.id.name, typeSymbolOpt)
          case _ => throw new Error("Illegal expression")
        }
      }.toSet
      currentScopeOpt.foreach(_.insert(FuncSymbol(fd.name.name, Some(returnTypeSymbol), paramSymbols)))
      val fnScope = new ScopedSymbolTable(fd.name.name, currentScopeOpt.get.scopeLevel + 1, currentScopeOpt)
      currentScopeOpt = Some(fnScope)
      paramSymbols.foreach(s => currentScopeOpt.foreach(_.insert(s)))
      fd.body.foreach(visit)

    case ret: STMT.Return => ret.value.foreach(visit)

    case expr: STMT.Expr => visit(expr.value)

    case ifStmt: STMT.If =>
      visit(ifStmt.test)
      ifStmt.body.foreach(visit)
      ifStmt.orelse.foreach(visit)

    case _ => // Do nothing.
  }

  private def visitExpr(node: EXPR): Unit = node match {

    case n: EXPR.Name => assertDefined(n.id.name)

    case bo: EXPR.BoolOp => bo.values.foreach(visit)

    case bin: EXPR.BinOp =>
      // TODO: Ensure operands are compatible.
      visit(bin.left)
      visit(bin.right)

    case fc: EXPR.Call =>
      fc.func match {
        case n: EXPR.Name =>
          val fn = currentScopeOpt.flatMap(_.lookup(n.id.name))
            .getOrElse(throw NameError(n.id.name))
          if (fn.asInstanceOf[FuncSymbol].params.size != fc.args.size + fc.keywords.size)
            throw WrongNumberOfArgumentsError(fn.name)
          fc.args.foreach(visit)
          fc.keywords.map(_.value).foreach(visit)
        case _ => throw IllegalExprError
      }

    case attr: EXPR.Attribute =>
      def getBase(node: AST_NODE): BuiltInTypeSymbol = node match {
        case name: EXPR.Name =>
          val sym = currentScopeOpt.flatMap(_.lookup(name.id.name))
            .getOrElse(throw NameError(name.id.name))
          sym match {
            case bis: BuiltInTypeSymbol => bis
            case _ => throw NotAnObjectError(sym.name)
          }
        case at: EXPR.Attribute => getBase(at.value)
        case _ => throw IllegalExprError
      }
      if (!getBase(attr.value).attributes.map(_.name).contains(attr.attr.name))
        throw NameError(attr.attr.name)

    case cmp: EXPR.Compare =>
      cmp.comparators.foreach(visit)
      visit(cmp.left)

    case uop: EXPR.UnaryOp => visit(uop.operand)

    case ifExp: EXPR.IfExp =>
      Seq(ifExp.test, ifExp.body, ifExp.orelse).foreach(visit)

    case dct: EXPR.Dict =>
      dct.keys.foreach(visit)
      dct.values.foreach(visit)

    case lst: EXPR.List => lst.elts.foreach(visit)

    case _ => // Do nothing.
  }

  private def visitDecl(node: EXPR.Decl): Unit = node.target match {
    case n: EXPR.Name =>
      val typeSymbolOpt = node.typeOpt.map { t =>
        assertDefined(t.name)
        BuiltInTypeSymbol(t.name)
      }
      currentScopeOpt.foreach(_.insert(VariableSymbol(n.id.name, typeSymbolOpt)))
    case _ => throw IllegalExprError
  }

  private def assertDefined(n: String): Unit = if (currentScopeOpt.flatMap(_.lookup(n)).isEmpty) throw NameError(n)

  def getType(exp: EXPR): TYPE = exp match {
    case expr: EXPR.TYPED_EXPR => expr.tpeOpt.getOrElse {
        expr match {
          case n: EXPR.Name => currentScopeOpt.flatMap(_.lookup(n.id.name))
            .map(r => Builtins.StaticBuiltInTypes.find(t => t.symbol.name == r.name).get.astType)
            .getOrElse(throw NameError(n.id.name))

          case fc: EXPR.Call => getType(fc.func)

          case bop: EXPR.BinOp =>
            (bop.left, bop.right) match {
              case (lt: EXPR.TYPED_EXPR, rt: EXPR.TYPED_EXPR) => Builtins.BinaryOperationResults.find {
                case (op, (o1, o2), _) => bop.op == op && o1 == getType(lt) && o2 == getType(rt)
              }.map(_._3).getOrElse(throw IllegalOperandError)
              case _ => throw IllegalExprError
            }

          case ifExp: EXPR.IfExp =>
            val bodyType = getType(ifExp.body)
            val elseType = getType(ifExp.orelse)
            if (bodyType != elseType) throw IllegalExprError
            bodyType

          case uop: EXPR.UnaryOp => getType(uop.operand)

          case _ => throw IllegalExprError
        }
      }
    case _ => throw IllegalExprError
  }
}