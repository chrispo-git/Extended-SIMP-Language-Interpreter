package simp

class FunctionEnv:
  private val fns  = scala.collection.mutable.Map[String, Decl.FnDecl]()

  private val builtins = scala.collection.mutable.Map[String, List[Value] => Value]()


  val methodTable = scala.collection.mutable.Map[(String, String), Decl.FnDecl]()

  def registerFn(name: String, fn: Decl.FnDecl): Unit = {
    fns(name) = fn
  }
  def lookupFn(name: String): Decl.FnDecl = {
    fns.getOrElse(name, throw RuntimeException(s"$name not found"))
  }
  def findNamespaced(name: String): Option[String] = fns.keys.find(k => k.endsWith(s"::$name"))
  
  def hasFn(name: String): Boolean = fns.contains(name)

  def registerBuiltin(name: String, fn: List[Value] => Value): Unit = {
    builtins(name) = fn
  }

  def lookupBuiltin(name: String): Option[List[Value] => Value] = {
    builtins.get(name)
  }

  def clear(): Unit = {
      fns.clear()
      methodTable.clear()
  }

  def dumpFn(): scala.collection.mutable.Map[String, Decl.FnDecl] = fns