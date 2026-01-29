import concurrent.duration.*

val d = FiniteDuration(5000000, NANOSECONDS)
FiniteDuration(
  d.toMillis,
  MILLISECONDS
)
