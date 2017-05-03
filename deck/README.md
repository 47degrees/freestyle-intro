<!-- .slide: class="intro" -->

![Freestyle Logo](custom/images/freestyle-logo.png)

# FREESTYLE

A COHESIVE & PRAGMATIC FRAMEWORK OF FP CENTRIC SCALA LIBRARIES

---

## Getting into FP is hard because...

- No CT knowledge or math foundations <!-- .element: class="fragment" --> 
- Leaving styles one is used to (ex. OOP) <!-- .element: class="fragment" --> 
- Lack of docs on how to properly use Monad Transformers and other techniques required to do concise FP. <!-- .element: class="fragment" -->
- Rapid changing ecosystem <!-- .element: class="fragment" -->
- Scala has not been designed to support first class typeclasses, sum types, etc. <!-- .element: class="fragment" -->
- Proliferation of IO-like types <!-- .element: class="fragment" -->

---

## Freestyle Goals : Approachable to newcomers 

Interface + Impl driven design

```scala
@free trait Interact {
  def ask(prompt: String): FS[String]
  def tell(msg: String): FS[Unit]
}

implicit val handler: Interact.Handler[Task] = new Interact.Handler[Task] {
  def ask(prompt: String): Task[String] = ???
  def tell(msg: String): Task[Unit] = ???
}
```

---

## Freestyle Goals : Stack-safe

Underlying impl is `cats.free.Free` which has stack-safety built in.

---

## Freestyle Goals : Boilerplate reduction

```diff
+ @free trait Interact {
+  def ask(prompt: String): FS[String]
+  def tell(msg: String): FS[Unit]
+ }
- sealed trait Interact[A]
- case class Ask(prompt: String) extends Interact[String]
- case class Tell(msg: String) extends Interact[Unit]

- class Interacts[F[_]](implicit I: InjectK[Interact, F]) {
-  def tell(msg: String): Free[F, Unit] = Free.inject[Interact, F](Tell(msg))
-  def ask(prompt: String): Free[F, String] = Free.inject[Interact, F](Ask(prompt))
- }

- object Interacts {
-  implicit def interacts[F[_]](implicit I: InjectK[Interact, F]): Interacts[F] = new Interacts[F]
-  def apply[F[_]](implicit ev: Interacts[F]): Interacts[F] = ev
- }
```

---

## Freestyle Goals : Boilerplate reduction

```diff
+ @module trait App {
+  val exerciseOp: ExerciseOp
+  val userOp: UserOp
+  val userProgressOp: UserProgressOp
+  val githubOp: GithubOp
+ }
- type C01[A] = Coproduct[ExerciseOp, UserOp, A]
- type C02[A] = Coproduct[UserProgressOp, C01, A]
- type ExercisesApp[A] = Coproduct[GithubOp, C02, A]
- val exerciseAndUserInterpreter: C01 ~> M = exerciseOpsInterpreter or userOpsInterpreter
- val userAndUserProgressInterpreter: C02 ~> M = userProgressOpsInterpreter or exerciseAndUserInterpreter
- val allInterpreters: ExercisesApp ~> M = githubOpsInterpreter or userAndUserProgressInterpreter
```

---

## Freestyle Goals : Predictable

Declare your algebras

```scala
/* Handles user interaction */
@free trait Interact {
  def ask(prompt: String): FS[String]
  def tell(msg: String): FS[Unit]
}

/* Represents persistence operations */
@free trait DataOp {
  def addCat(a: String): FS[Unit]
  def getAllCats : FS[List[String]]
}
```

---

## Freestyle Goals : Predictable

Combine your algebras in arbitrarily nested modules

```scala
/* Handles user interaction */
@module trait Persistence {
  val interact: Interact
  val dataOp: DataOp
}

@module trait App {
  val persistence: Persistence
  ...  
}
```

---

## Freestyle Goals : Predictable

Declare and composes pieces of your programs

```scala
def program[F[_]: Interacts : DataSource]: FreeS[F, Unit] = {
  val I = Interacts[F]
  val D = DataSource[F]
  for {
    cat <- I.ask("What's the kitty's name?")
    _ <- D.addCat(cat)
    cats <- D.getAllCats
    _ <- I.tell(cats.toString)
  } yield ()
}
```

---

## Freestyle Goals : Predictable

Provide implicit evidence of your handlers to any desired target `M[_]`

```scala
implicit val interactHandler: Interact.Handler[Task] = new Interact.Handler[Task] {
  def ask(prompt: String): Task[String] = ???
  def tell(msg: String): Task[Unit] = ???
}

implicit val dataOpsHandler: DataOp.Handler[Task] = new DataOp.Handler[Task] {
  def addCat(a: String): FS[Unit] = ???
  def getAllCats : FS[List[String]] = ???
}
```

---

## Freestyle Goals : Predictable

Run your program to your desired `M[_]`

```scala
program[App.Op].interpret[Task]
```

---

## Freestyle Goals : Third party framework integrations

1. Create an algebra with the third party datatype

```scala
@free sealed trait DoobieM {
  def transact[A](f: ConnectionIO[A]): FS[A]
}
```

---

## Freestyle Goals : Third party framework integrations

2. Implement a handler declaring the target `M[_]` and whatever restrictions it may have

```scala
implicit def freeStyleDoobieHandler[M[_]: Catchable: Suspendable]
  (implicit xa: Transactor[M]): DoobieM.Handler[M] =
      new DoobieM.Handler[M] {
        def transact[A](fa: ConnectionIO[A]): M[A] = fa.transact(xa)
      }
```

---

## Freestyle Goals : Third party framework integrations

3. Optionally provide syntax for easy embedding into program's flow

```scala
implicit def freeSLiftDoobie[F[_]: DoobieM]: FreeSLift[F, ConnectionIO] =
  new FreeSLift[F, ConnectionIO] {
    def liftFSPar[A](cio: ConnectionIO[A]): FreeS.Par[F, A] = DoobieM[F].transact(cio)
  }
```

---

## Freestyle Goals : Third party framework integrations

4. Use third party types interleaved with other algebras and effects

```scala
def loadUser[F[_]]
  (userId: UserId)
  (implicit 
    doobie: DoobieM[F], 
    logging: LoggingM[F]): FreeS[F, User] = {
    import doobie.implicits._
    for {
      user <- (sql"SELECT * FROM User WHERE userId = $userId"
                .query[User]
                .unique
                .liftFS[F])
      - <- logging.debug(s"Loaded User: ${user.userId}")
    } yield user
}
```

---

## Freestyle Goals : Easy parallelism

All `FS[_]` ops are `FreeApplicative` and potentially parallelizable

```scala
@free trait UserInput {
  def getText: FS[String]
}

@free trait Validation {
  def minSize(s: String, n: Int): FS[Boolean]
  def hasNumber(s: String): FS[Boolean]
}

def program[F[_]](implicit U: UserInput[F], V : Validation[F]) = {
  import U._, V._
  for {
    userText <- getText
    isValid <- (minSize(userText, 10) |@| hasNumber(userText)).map(_ && _)
  } yield isValid
}
```

---

## Freestyle Goals : Boilerplate reduction

1. Approachable to newcomers <!-- .element: class="fragment" -->
2. Stack-safe <!-- .element: class="fragment" -->
3. Boilerplate reduction <!-- .element: class="fragment" -->
4. Predictable API <!-- .element: class="fragment" -->
5. Return type agnostic <!-- .element: class="fragment" -->
6. Easy third party integrations

---

## Algebras

- Inspired by Simulacrum `@typeclass`
- Auto implicit instances
- Smart constructors 
- FunctionK handlers (tagless style)

---

## Before @free

```scala
/* Handles user interaction */
sealed trait Interact[A]
case class Ask(prompt: String) extends Interact[String]
case class Tell(msg: String) extends Interact[Unit]

class Interacts[F[_]](implicit I: InjectK[Interact, F]) {
  def tell(msg: String): Free[F, Unit] = Free.inject[Interact, F](Tell(msg))
  def ask(prompt: String): Free[F, String] = Free.inject[Interact, F](Ask(prompt))
}

object Interacts {
  implicit def interacts[F[_]](implicit I: InjectK[Interact, F]): Interacts[F] = new Interacts[F]
}

/* Represents persistence operations */
sealed trait DataOp[A]
case class AddCat(a: String) extends DataOp[Unit]
case class GetAllCats() extends DataOp[List[String]]

class DataSource[F[_]](implicit I: InjectK[DataOp, F]) {
  def addCat(a: String): Free[F, Unit] = Free.inject[DataOp, F](AddCat(a))
  def getAllCats: Free[F, List[String]] = Free.inject[DataOp, F](GetAllCats())
}

object DataSource {
  implicit def dataSource[F[_]](implicit I: InjectK[DataOp, F]): DataSource[F] = new DataSource[F]
}
```

---

## After @free

```scala
/* Handles user interaction */
@free trait Interact {
  def ask(prompt: String): FS[String]
  def tell(msg: String): FS[Unit]
}

/* Represents persistence operations */
@free trait DataOp {
  def addCat(a: String): FS[Unit]
  def getAllCats : FS[List[String]]
}
```

---

## Building programs

```scala
def program[F[_]](implicit I : Interacts[F], D : DataSource[F]): FreeS[F, Unit] = {
  import I._, D._
  for {
    cat <- ask("What's the kitty's name?")
    _ <- addCat(cat)
    cats <- getAllCats
    _ <- tell(cats.toString)
  } yield ()
}
```

---

## @tagless representations

---

## Handlers

---

## Modules

- iota

---

## Avoid using transformers

Error

```scala
import freestyle.effects.error._
import freestyle.effects.error.implicits._

val boom = new RuntimeException("BOOM")

type Target[A] = Either[Throwable, A]

def shortCircuit[F[_]: ErrorM] =
  for {
    a <- FreeS.pure(1)
    b <- ErrorM[F].error[Int](boom)
    c <- FreeS.pure(1)
  } yield a + b + c

shortCircuit[ErrorM.Op].interpret[Target]
// res0: Target[Int] = Left(java.lang.RuntimeException: BOOM)
```

---

## Avoid using transformers

Option

```scala
import freestyle.effects.option._
import freestyle.effects.option.implicits._

def programNone[F[_]: OptionM] =
  for {
    a <- FreeS.pure(1)
    b <- OptionM[F].option[Int](None)
    c <- FreeS.pure(1)
  } yield a + b + c

programNone[OptionM.Op].interpret[Option]
// res0: Option[Int] = None
```

---

## Avoid using transformers

Validation

```scala
sealed trait ValidationError
case class NotValid(explanation: String) extends ValidationError

val vl = validation[ValidationError]
import vl.implicits._

type ValidationResult[A] = State[List[ValidationError], A]

def programErrors[F[_]: vl.ValidationM] =
  for {
    _ <- vl.ValidationM[F].invalid(NotValid("oh no"))
    errs <- vl.ValidationM[F].errors
    _ <- vl.ValidationM[F].invalid(NotValid("this won't be in errs"))
  } yield errs

programErrors[vl.ValidationM.Op].interpret[ValidationResult].runEmpty
// res5: cats.Eval[(List[ValidationError], List[ValidationError])] = cats.Eval$$anon$8@115029c1
```

---

## Effects

- error
- either
- option
- reader
- writer
- state
- traverse
- validation
- async

---

## Integrations

- Monix
- Fetch
- FS2
- Doobie
- Slick
- Akka Http
- Play
- Finch
- Http4s

---

## What's next?

- kafka
- cassandra
- rpc

---

## Inspired by

-

---

### Features ###

| *Error Handling* | *When to use*               | *Java* | *Kotlin* | *Scala* |
|------------------|--------------------------- -|--------|----------|---------|
| *Exceptions*     | ~Never                      | x      | x        | x       |
| *Option*         | Modeling Absence            | ?      | x        | x       |
| *Try*            | Capturing Exceptions        | ?      | ?        | x       |
| *Either*         | Modeling Alternate Paths    | ?      | ?        | x       |
| *MonadError*     | Abstracting away concerns   | -      | -        | x       |

---

### Thanks! ###
 
http://frees.io 
@raulraja @47deg

---
