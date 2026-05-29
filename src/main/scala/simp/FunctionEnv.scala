package simp

class FunctionEnv:
  private val fns  = scala.collection.mutable.Map[String, Decl.FnDecl]()
  private val procs = scala.collection.mutable.Map[String, Decl.PdDecl]()

  private val builtins = scala.collection.mutable.Map[String, List[Value] => Value]()

  def registerFn(name: String, fn: Decl.FnDecl): Unit = {
    fns(name) = fn
  }
  def lookupFn(name: String): Decl.FnDecl = {
    fns.getOrElse(name, throw RuntimeException(s"$name not found"))
  }
  def findNamespaced(name: String): Option[String] = fns.keys.find(k => k.endsWith(s"::$name"))
  
  def hasFn(name: String): Boolean = fns.contains(name)

  def registerPd(name: String, pd: Decl.PdDecl): Unit = {
    procs(name) = pd
  }
  def lookupPd(name: String): Decl.PdDecl = {
    procs.getOrElse(name, throw RuntimeException(s"$name not found"))
  }

  def registerBuiltin(name: String, fn: List[Value] => Value): Unit = {
    builtins(name) = fn
  }

  def lookupBuiltin(name: String): Option[List[Value] => Value] = {
    builtins.get(name)
  }

  def dumpFn(): scala.collection.mutable.Map[String, Decl.FnDecl] = fns
  def dumpPd(): scala.collection.mutable.Map[String, Decl.PdDecl] = procs