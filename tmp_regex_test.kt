fun main() {
  val r = Regex("""^(?![_\-\u0020])(?:(?!')[a-zA-Z0-9\p{L}\p{N}\p{So}_\-\s]){1,64}$""")
  println(r.matches("new-channel"))
  println(r.matches("test"))
}
