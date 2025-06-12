package dev.cheleb.ziomagnum

final case class ZTransaction(connection: java.sql.Connection)
    extends AutoCloseable {

  def commit(): ZTransaction = {
    connection.commit()
    this
  }

  def rollback(): ZTransaction = {
    connection.rollback()
    this
  }

  def close(): Unit = {
    connection.close()
  }
}

object ZTransaction {
  def apply(connection: java.sql.Connection): ZTransaction = {
    connection.setAutoCommit(false)
    new ZTransaction(connection)
  }
}
