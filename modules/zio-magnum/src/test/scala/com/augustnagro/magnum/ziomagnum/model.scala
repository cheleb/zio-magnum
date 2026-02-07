package com.augustnagro.magnum.ziomagnum

import com.augustnagro.magnum.*
import scala.language.implicitConversions
import java.util.UUID

@SqlName("users")
@Table(PostgresDbType, SqlNameMapper.CamelToSnakeCase)
case class UserCreator(
    name: String,
    photo: Option[Array[Byte]],
    myuuid: UUID,
    nullableUuid: Option[User.Id]
) derives DbCodec
@SqlName("users")
@Table(PostgresDbType, SqlNameMapper.CamelToSnakeCase)
case class User(
    @Id id: Int,
    name: String,
    photo: Option[Array[Byte]],
    myuuid: UUID,
    nullableUuid: Option[User.Id]
) derives DbCodec
@SqlName("users")
@Table(PostgresDbType, SqlNameMapper.CamelToSnakeCase)
case class UserUnordered(
    @Id id: Int,
    myuuid: UUID,
    name: String,
    photo: Option[Array[Byte]],
    nullableUuid: Option[User.Id]
) derives DbCodec

object User:
  opaque type Id <: UUID = UUID

  object Id:
    def apply(uuid: UUID): Id = uuid

    given DbCodec[Id] = DbCodec.UUIDCodec.biMap[Id](identity, User.Id.apply)

@SqlName("projects")
@Table(PostgresDbType, SqlNameMapper.CamelToSnakeCase)
case class Project(
    @Id id: Int,
    name: String
) derives DbCodec
