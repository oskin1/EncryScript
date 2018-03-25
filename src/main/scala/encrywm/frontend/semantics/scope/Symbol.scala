package encrywm.frontend.semantics.scope

trait Symbol {

  val name: String
  val tpeOpt: Option[BuiltInTypeSymbol] = None
}

case class BuiltInTypeSymbol(override val name: String,
                             typeParams: Seq[BuiltInTypeSymbol] = Seq(),
                             attributes: Seq[VariableSymbol] = Seq()) extends Symbol

case class FuncSymbol(override val name: String,
                      override val tpeOpt: Option[BuiltInTypeSymbol],
                      params: Seq[VariableSymbol] = Seq()) extends Symbol

case class VariableSymbol(override val name: String,
                          override val tpeOpt: Option[BuiltInTypeSymbol]) extends Symbol
