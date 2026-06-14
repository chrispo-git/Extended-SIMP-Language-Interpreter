package simp

// Handles the Memory of the environment
class Store(val parent: Option[Store] = None):
    private val memory = scala.collection.mutable.Map[String, Value]()
    private val consts = scala.collection.mutable.Set[String]()

    private def hasLocal(name: String): Boolean = memory.contains(name)
    private def isLocalConst(name: String): Boolean = consts.contains(name)
    private def setLocal(name: String, value: Value): Unit = memory(name) = value
    private def getLocal(name: String): Value = memory(name)

    private def findOwner(name: String): Option[Store] = {
        if memory.contains(name) then {
            Some(this)
        } else {
            parent.flatMap(_.findOwner(name))
        }
    }

    def load(loc: String): Value = findOwner(loc) match {
        case Some(owner) => owner.getLocal(loc)
        case None => throw RuntimeException(s"Unbound location: $loc")
    }

    def store(loc: String, value: Value): Unit = {
        if loc != "_" then {
            findOwner(loc) match {
                case Some(owner) => {
                    if owner.consts.contains(loc) then {
                        throw RuntimeException(s"Can't assign to const: '$loc'")
                    }
                    owner.setLocal(loc, value)
                }
                case None => setLocal(loc, value)
            }
        }
    }

    def declareConst(loc: String, value: Value): Unit = {
        if loc != "_" then {
            setLocal(loc, value)
            consts += loc
        }
    }

    def contains(name: String): Boolean = findOwner(name).isDefined
    
    def remove(name: String): Unit = {
        memory.remove(name)
        consts.remove(name)
    }

    def clear(): Unit = {
        memory.clear()
        consts.clear()
    }

    def child(): Store = Store(Some(this))

    def dump(): Unit = memory.toSeq.sortBy(_._1).foreach((k, v) => println(s"$k = $v"))

    def entries(): Iterable[(String, Value)] = memory