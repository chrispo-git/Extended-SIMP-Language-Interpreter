package simp

class Store:
    private val memory = scala.collection.mutable.Map[String, Int]()

    def load(loc: String): Int = memory.getOrElse(loc, throw RuntimeException(s"Unbound location: $loc"))

    def store(loc: String, value: Int): Unit = memory(loc) = value

    def dump(): Unit = memory.toSeq.sortBy(_._1).foreach((k, v) => println(s"$k = $v"))