package simp

class FunctionEnv:
  private val fns  = scala.collection.mutable.Map[String, Decl.FnDecl]()
  private val procs = scala.collection.mutable.Map[String, Decl.PdDecl]()

  def registerFn(name: String, fn: Decl.FnDecl): Unit = {
    fns(name) = fn
  }
  def lookupFn(name: String): Decl.FnDecl = {
    fns.getOrElse(name, throw RuntimeException(s"$name not found"))
  }
  def registerPd(name: String, pd: Decl.PdDecl): Unit = {
    procs(name) = pd
  }
  def lookupPd(name: String): Decl.PdDecl = {
    procs.getOrElse(name, throw RuntimeException(s"$name not found"))
  }