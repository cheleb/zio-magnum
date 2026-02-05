package com.augustnagro.magnum.ziomagnum

import com.augustnagro.magnum.*
import scala.language.implicitConversions
import java.util.UUID

@SqlName("users")
@Table(PostgresDbType, SqlNameMapper.CamelToSnakeCase)
case class User(
    @Id id: Int,
    name: String,
    photo: Option[Array[Byte]],
    myuuid: UUID
) derives DbCodec

@SqlName("projects")
@Table(PostgresDbType, SqlNameMapper.CamelToSnakeCase)
case class Project(
    @Id id: Int,
    name: String
) derives DbCodec
