package encrywm.lang.frontend.semantics

import encrywm.ast.Ast.EXPR
import encrywm.ast.Ast
import encrywm.lib.Types.{ESFunc, ESList, ESOption}
import org.bitbucket.inkytonik.kiama.rewriting.Rewriter._

object Transformer {

  def transform(node: Ast.AST_NODE): Ast.AST_NODE = rewrite(manytd(strategy[Ast.AST_NODE]({

    // Rule: Call(Attribute(coll, "exists"), Func) -> Exists(coll, Func)
    case EXPR.Call(EXPR.Attribute(value, attr, _, _), args, _, _)
      if value.tpeOpt.get.isCollection &&
        attr.name == "exists" &&
        args.size == 1 &&
        args.head.tpeOpt.get.isFunc =>
      Some(EXPR.Exists(value, args.head))

    // Rule: Call(Attribute(coll, "map"), Func) -> Map(coll, Func)
    case EXPR.Call(EXPR.Attribute(value, attr, _, _), args, _, Some(tpe))
      if value.tpeOpt.get.isCollection &&
        attr.name == "map" &&
        args.size == 1 &&
        args.head.tpeOpt.get.isFunc =>
      Some(EXPR.Map(value, args.head, Some(tpe)))

    // Rule: Attribute(coll, "size") -> SizeOf(coll)
    case EXPR.Attribute(value, attr, _, _)
      if value.tpeOpt.get.isCollection && attr.name == "size" =>
        Some(EXPR.SizeOf(value))

    // Rule: Attribute(coll, "sum") -> SizeOf(coll)
    case EXPR.Attribute(value, attr, _, _)
      if value.tpeOpt.get.isCollection && attr.name == "sum" =>
      value.tpeOpt match {
        case Some(ESList(valT)) =>
          Some(EXPR.Sum(value, Some(valT)))
        case _ => None
      }

    // Rule: Attribute(option, "isDefined") -> IsDefined(option)
    case EXPR.Attribute(value, attr, _, _)
      if value.tpeOpt.get.isOption && attr.name == "isDefined" =>
      Some(EXPR.IsDefined(value))

    // Rule: Attribute(option, "get") -> Get(option)
    case EXPR.Attribute(value, attr, _, _) if attr.name == "get" =>
      value.tpeOpt.get match {
        case ESOption(inT) => Some(EXPR.Get(value, Some(inT)))
        case ESFunc(_, ESOption(inT)) => Some(EXPR.Get(value, Some(inT)))
        case _ => None
      }

    case _ => None
  })))(node)
}