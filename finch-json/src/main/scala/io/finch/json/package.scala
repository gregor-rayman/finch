package io.finch

package object json {

  /**
   *
   */
  sealed trait Json {

    /**
     *
     */
    override def toString: String = Json.encode(this)

    /**
     *
     */
    def merge(that: Json): Json = Json.mergeLeft(this, that)

    /**
     *
     */
    def concat(that: Json): Json = Json.concatLeft(this, that)

    /**
     *
     */
    def compressed: Json = Json.compress(this)

    /**
     *
     */
    def query[A](path: String): Option[A] = {
      def loop(path: List[String], outer: Map[String, Any]): Option[A] = path match {
        case tag :: Nil => outer.get(tag) map { _.asInstanceOf[A] }
        case tag :: tail => outer.get(tag) match {
          case Some(JsonObject(inner)) => loop(tail, inner)
          case _ => None
        }
      }

      // for now we don't query arrays
      this match {
        case JsonObject(map) => loop(path.split('.').toList, map)
        case _ => None
      }
    }
  }

  case class JsonObject(map: Map[String, Any]) extends Json

  case class JsonArray(list: List[Any]) extends Json

  object JsonNull extends Json

  object Json {

    /**
     * Creates an empty json object
     *
     * @return an empty json object
     */
    def emptyObject: Json = JsonObject(Map.empty[String, Any])

    /**
     * Creates an empty json array.
     *
     * @return an empty json array.
     */
    def emptyArray: Json = JsonArray(List.empty[Any])

    /**
     * Creates a new json array of given sequence of items ''args''.
     *
     * @param args sequence of items in the array
     *
     * @return a new json array
     */
    def arr(args: Any*): Json = JsonArray(args.toList)

    /**
     * Creates a json object of given sequence of properties ''args''. Every
     * argument/property is a pair of ''tag'' and ''value'' associated with it.
     * It's possible to pass a complete json path (separated by dot) as ''tag''.
     *
     * @param args a sequence of json properties
     *
     * @return a json object
     */
    def obj(args: (String, Any)*): Json = {
      def loop(path: List[String], value: Any): Map[String, Any] = path match {
        case tag :: Nil => Map(tag -> value)
        case tag :: tail => Map(tag -> JsonObject(loop(tail, value)))
      }

      val jsonSeq = args.flatMap {
        case (path, value) =>
          Seq(JsonObject(loop(path.split('.').toList, if (value == null) JsonNull else value)))
      }

      jsonSeq.foldLeft(Json.emptyObject) { Json.mergeRight(_, _) }
    }


    def decode(s: String): Json = JsonNull

    def encode(j: Json): String = {
      def escape(s: String) = s flatMap {
        case '"'  => "\\\""
        case '\\' => "\\\\"
        case '\b' => "\\b"
        case '\f' => "\\f"
        case '\n' => "\\n"
        case '\r' => "\\r"
        case '\t' => "\\t"
        case c => c.toString
      }

      def wire(any: Any): String = any match {
        case s: String => escape(s)
        case JsonObject(map) => "{" + map.map({ case (k,v) => wire(k.toString) + " : " + wire(v) }) + "}"
        case JsonArray(list) => "[" + list.map(wire).mkString(",") + "]"
        case other => other.toString
      }

      wire(j)
    }

    /**
     * Deeply merges given json objects ''a'' and ''b'' into a single json object.
     * In case of conflict tag the value of a right json object will be taken.
     *
     * @param a the left json object
     * @param b the right json object
     *
     * @return a merged json object
     */
    def mergeRight(a: Json, b: Json): Json = mergeLeft(b, a)

    /**
     * Deeply merges given json objects ''a'' and ''b'' into a single json object.
     * In case of conflict tag the value of a left json object will be taken.
     *
     * @param a the left json object
     * @param b the right json object
     *
     * @return a merged json object
     */
    def mergeLeft(a: Json, b: Json): Json = {
      def loop(aa: Map[String, Any], bb: Map[String, Any]): Map[String, Any] =
        if (aa.isEmpty) bb
        else if (bb.isEmpty) aa
        else {
          val (tag, value) = aa.head
          if (!bb.contains(tag)) loop(aa.tail, bb + (tag -> value))
          else (value, bb(tag)) match {
            case (ja: JsonObject, jb: JsonObject) =>
              loop(aa.tail, bb + (tag -> JsonObject(loop(ja.map, jb.map))))
            case (_, _) => loop(aa.tail, bb + (tag -> value))
          }
        }

      (a, b) match {
        case (JsonObject(aa), JsonObject(bb)) => JsonObject(loop(aa, bb))
        case _ => JsonNull
        // TODO: How to merge arrays?
        // TODO: How to merge array with object
      }
    }

    /**
     *
     */
    def concatRight(a: Json, b: Json): Json = concatLeft(b, a)

    /**
     * Concatenates two given json object ''a'' and ''b''.
     *
     * @param a the left object
     * @param b the right object
     *
     * @return a concatenated array
     */
    def concatLeft(a: Json, b: Json): Json = (a, b) match {
      case (JsonObject(aa), JsonObject(bb)) => JsonObject(aa ++ bb)
      case (JsonArray(aa), JsonArray(bb)) => JsonArray(aa ::: bb)
      case (JsonArray(aa), bb: JsonObject) => JsonArray(aa :+ bb)
      case (aa: JsonObject, JsonArray(bb)) => JsonArray(aa :: bb) // TODO: This is not clear
      case _ => JsonNull
    }

    /**
     * Removes all null-value properties from this json object.
     *
     * @return a compressed json object
     */
    def compress(j: Json): Json = {
      def loop(obj: Map[String, Any]): Map[String, Any] = obj.flatMap {
        case (t, JsonNull) => Map.empty[String, Any]
        case (tag, JsonObject(map)) =>
          val o = loop(map)
          if (o.isEmpty) Map.empty[String, Any]
          else Map(tag -> JsonObject(o))
        case (tag, value) => Map(tag -> value)
      }

      j match {
        case JsonNull => JsonNull
        case JsonObject(map) => JsonObject(loop(map))
        case JsonArray(list) => JsonArray(list.map {
          case jj: Json => Json.compress(jj)
          case ii => ii
        })
      }
    }
  }

  implicit object EncodeDeprecatedJson extends EncodeJson[Json] {
    def apply(json: Json): String = Json.encode(json)
  }

  implicit object DecodeDeprecatedJson extends DecodeJson[Json] {
    def apply(json: String): Json = Json.decode(json)
  }
}
