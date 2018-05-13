package encrywm.frontend.semantics

import encrywm.ast.Ast.{EXPR, Identifier, STMT}
import encrywm.ast.Ast
import org.bitbucket.inkytonik.kiama.rewriting.Rewriter._
import scorex.crypto.encode.Base58
import scorex.crypto.hash.Blake2b256

object Optimizer {

  def scan(node: Ast.AST_NODE): Ast.AST_NODE = rewrite(manytd(strategy[Ast.AST_NODE]({
    case STMT.Let(EXPR.Declaration(EXPR.Name(Identifier(n), ctx, t), to), v, g) if n.length > 3 =>
      val name = Base58.encode(Blake2b256.hash(n)).take(3)
      Some(STMT.Let(EXPR.Declaration(EXPR.Name(name, ctx, t), to), v, g))
    case _ => None
  })))(node)
}
