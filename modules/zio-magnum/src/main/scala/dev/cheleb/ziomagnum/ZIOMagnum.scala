package dev.cheleb.ziomagnum

import zio.*
import com.augustnagro.magnum.*

case class User(id: Int, name: String)

class ZIOMagnum(transactor: Transactor):

  val users: Vector[User] = connect(transactor):
    sql"SELECT * FROM user".query[User].run()

override def toString(): String = "ZIOMagnum"

object ZIOMagnum:

  def run[A](frag: Frag): ZIO[DbCon, Throwable, A] =
    for dbCon <- ZIO.service[DbCon]
    yield ???
