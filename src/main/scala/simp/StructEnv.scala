package simp

class StructEnv:
    private val structs = scala.collection.mutable.Map[String, StructDef]()

    def register(name: String, defn: StructDef): Unit = structs(name) = defn
    def lookup(name: String): StructDef = {
        structs.getOrElse(name, throw RuntimeException(s"Unknown struct type: $name"))
    }
    def exists(name: String): Boolean = structs.contains(name)

    def preRegister(name: String): Unit = structs(name) = StructDef(List())

case class StructDef(fields: List[(String, SimpType)])