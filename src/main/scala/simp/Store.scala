package simp

// Handles the Memory of the environment
class Store:
    private val memory = scala.collection.mutable.Map[String, Value]()

    def load(loc: String): Value = memory.getOrElse(loc, throw RuntimeException(s"Unbound location: $loc"))

    def store(loc: String, value: Value): Unit = memory(loc) = value

    def dump(): Unit = memory.toSeq.sortBy(_._1).foreach((k, v) => println(s"$k = $v"))