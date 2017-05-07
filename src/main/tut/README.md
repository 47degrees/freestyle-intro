<!-- .slide: class="intro" -->

![Freestyle Logo](custom/images/freestyle-logo.png)

# FREESTYLE

A COHESIVE & PRAGMATIC FRAMEWORK OF FP CENTRIC SCALA LIBRARIES

---

<!-- .slide: class="center" -->

## Getting into typed FP is hard because...

- No previous CT knowledge or math foundations <!-- .element: class="fragment" --> 
- Leaving styles one is used to (ex. OOP) <!-- .element: class="fragment" --> 
- Lack of docs on how to properly use Monad Transformers and other techniques required to do concise FP. <!-- .element: class="fragment" -->
- Rapid changing ecosystem <!-- .element: class="fragment" -->
- Scala has not been designed to support first class typeclasses, sum types, etc. <!-- .element: class="fragment" -->
- Proliferation of IO-like types <!-- .element: class="fragment" -->

---

## Freestyle Goals

- Approachable to newcomers <!-- .element: class="fragment" -->
- Stack-safe <!-- .element: class="fragment" -->
- Dead simple integrations with Scala's library ecosystem <!-- .element: class="fragment" -->

---

## In this talk

- Freestyle programming style
- @free, @tagless, @modules
- Effects
- Integrations
- Misc goodies (iota Coproduct, friendly error messages, macro debug)

---

## Interface + Impl driven design

```scala
@free trait Interact {
  def ask(prompt: String): FS[String]
  def tell(msg: String): FS[Unit]
}

implicit val handler: Interact.Handler[Future] = new Interact.Handler[Future] {
  def ask(prompt: String): Future[String] = ???
  def tell(msg: String): Future[Unit] = ???
}
```

---

## Boilerplate Reduction > Declaration

```diff
+ @free trait Interact {
+  def ask(prompt: String): FS[String]
+  def tell(msg: String): FS[Unit]
+ }
- sealed trait Interact[A]
- case class Ask(prompt: String) extends Interact[String]
- case class Tell(msg: String) extends Interact[Unit]
-
- class Interacts[F[_]](implicit I: InjectK[Interact, F]) {
-  def tell(msg: String): Free[F, Unit] = Free.inject[Interact, F](Tell(msg))
-  def ask(prompt: String): Free[F, String] = Free.inject[Interact, F](Ask(prompt))
- }
-
- object Interacts {
-  implicit def interacts[F[_]](implicit I: InjectK[Interact, F]): Interacts[F] = new Interacts[F]
-  def apply[F[_]](implicit ev: Interacts[F]): Interacts[F] = ev
- }
```

---

## Boilerplate Reduction > Composition

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

## Predictable Workflow

- Declare your algebras <!-- .element: class="fragment" -->
- Group them into modules <!-- .element: class="fragment" -->
- Compose your programs <!-- .element: class="fragment" -->
- Provide implicit implementations of each algebra Handler <!-- .element: class="fragment" -->
- Run your programs at the edge of the world <!-- .element: class="fragment" -->

---

## Predictable Workflow

Declare your algebras

```tut:silent
import freestyle._

object algebras {
  /* Handles user interaction */
  @free trait Interact {
    def ask(prompt: String): FS[String]
    def tell(msg: String): FS[Unit]
  }

  /* Validates user input */
  @free trait Validation {
    def minSize(s: String, n: Int): FS[Boolean]
    def hasNumber(s: String): FS[Boolean]
  }
}
```

---

## Predictable Workflow

Combine your algebras in arbitrarily nested modules

```tut:silent
import algebras._
import freestyle.effects.error._
import freestyle.effects.error.implicits._
import freestyle.effects.state
val st = state[List[String]]
import st.implicits._

object modules {

  @module trait App {
    val validation: Validation
    val interact: Interact
    val errorM : ErrorM
    val persistence: st.StateM
  }
  
}
```

---

## Predictable Workflow

Declare and compose programs

```tut:silent
import cats.syntax.cartesian._

def program[F[_]]
  (implicit I: Interact[F], R: st.StateM[F], E: ErrorM[F], V: Validation[F]): FreeS[F, Unit] = {
  for {
    cat <- I.ask("What's the kitty's name?")
    isValid <- (V.minSize(cat, 5) |@| V.hasNumber(cat)).map(_ && _) //may run ops in parallel
    _ <- if (isValid) R.modify(cat :: _) else E.error(new RuntimeException("invalid name!"))
    cats <- R.get
    _ <- I.tell(cats.toString)
  } yield ()
}
```

---

## Predictable Workflow

Provide implicit evidence of your handlers to any desired target `M[_]`

```tut:silent
import monix.eval.Task
import monix.cats._
import cats.syntax.flatMap._
import cats.data.StateT

type Target[A] = StateT[Task, List[String], A]

implicit val interactHandler: Interact.Handler[Target] = new Interact.Handler[Target] {
  def ask(prompt: String): Target[String] = tell(prompt) >> StateT.lift(Task.now("Isidoro1"))
  def tell(msg: String): Target[Unit] = StateT.lift(Task { println(msg) })
}

implicit val validationHandler: Validation.Handler[Target] = new Validation.Handler[Target] {
  def minSize(s: String, n: Int): Target[Boolean] = StateT.lift(Task.now(s.length >= n))
  def hasNumber(s: String): Target[Boolean] = StateT.lift(Task.now(s.exists(c => "0123456789".contains(c))))
}
```

---

## Predictable Workflow

Run your program to your desired `M[_]`

```tut:silent
import modules._
import freestyle.implicits._
import cats.instances.list._
import monix.execution.Scheduler.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration._
```
```tut:book
val concreteProgram = program[App.Op]
val state = concreteProgram.interpret[Target]
val task = state.runEmpty
val asyncResult = task.runAsync

Await.result(asyncResult, 3.seconds)
```

---

## Effects

Error

```tut:book
import freestyle.effects.error._
import freestyle.effects.error.implicits._
import cats.instances.either._

type EitherTarget[A] = Either[Throwable, A]

def shortCircuit[F[_]: ErrorM] =
  for {
    a <- FreeS.pure(1)
    b <- ErrorM[F].error[Int](new RuntimeException("BOOM"))
    c <- FreeS.pure(1)
  } yield a + b + c

shortCircuit[ErrorM.Op].interpret[EitherTarget]
shortCircuit[ErrorM.Op].interpret[Task]
```

---

## Effects

Option

```tut:book
import freestyle.effects.option._
import freestyle.effects.option.implicits._
import cats.instances.option._
import cats.instances.list._

def programNone[F[_]: OptionM] =
  for {
    a <- FreeS.pure(1)
    b <- OptionM[F].option[Int](None)
    c <- FreeS.pure(1)
  } yield a + b + c

programNone[OptionM.Op].interpret[Option]
programNone[OptionM.Op].interpret[List]
```

---

## Effects

Validation

```tut:book
import freestyle.effects.validation
import cats.data.State

sealed trait ValidationError
case class NotValid(explanation: String) extends ValidationError

val v = validation[ValidationError]
import v.implicits._

type ValidationResult[A] = State[List[ValidationError], A]

def programErrors[F[_]: v.ValidationM] =
  for {
    _ <- v.ValidationM[F].invalid(NotValid("oh no"))
    errs <- v.ValidationM[F].errors
    _ <- v.ValidationM[F].invalid(NotValid("this won't be in errs"))
  } yield errs

programErrors[v.ValidationM.Op].interpret[ValidationResult].runEmpty.value
```

---

## Effects

An alternative to monad transformers

<div> error: Signal errors </div> <!-- .element: class="fragment" -->
<div> either: Flattens if `Right` / short-circuit `Left` </div> <!-- .element: class="fragment" --> 
<div> option: Flatten `Some` / short-circuit on `None` </div> <!-- .element: class="fragment" -->
<div> reader: Deffer dependency injection until program interpretation </div> <!-- .element: class="fragment" -->
<div> writer: Log / Accumulate values </div> <!-- .element: class="fragment" -->
<div> state: Pure functional state threaded over the program monadic sequence </div> <!-- .element: class="fragment" -->
<div> traverse: Generators over `Foldable` </div> <!-- .element: class="fragment" -->
<div> validation: Accumulate and inspect errors throughout the monadic sequence </div> <!-- .element: class="fragment" -->
<div> async: Integrate with callback based API's </div> <!-- .element: class="fragment" -->

---

## Integrations

<div> Monix: Target runtime and `async` effect integration. </div> <!-- .element: class="fragment" -->
<div> Fetch: Algebra to run fetch instances + Auto syntax `Fetch -> FS`. </div> <!-- .element: class="fragment" -->
<div> FS2: Embed FS2 `Stream` in Freestyle programs. </div> <!-- .element: class="fragment" -->
<div> Doobie: Embed `ConnectionIO` programs into Freestyle. </div> <!-- .element: class="fragment" -->
<div> Slick: Embed `DBIO` programs into Freestyle. </div> <!-- .element: class="fragment" -->
<div> Akka Http: `EntityMarshaller`s to return Freestyle programs in Akka-Http endpoints. </div> <!-- .element: class="fragment" -->
<div> Play: Implicit conversions to return Freestyle programs in Play Actions. </div> <!-- .element: class="fragment" -->
<div> Twitter Util: `Capture` instances for Twitter's `Future` & `Try`. </div> <!-- .element: class="fragment" -->
<div> Finch: Mapper instances to return Freestyle programs in Finch endpoints. </div> <!-- .element: class="fragment" -->
<div> Http4s: `EntityEncoder` instance to return Freestyle programs in Http4S endpoints. </div> <!-- .element: class="fragment" -->

---

## Integrations

1. Create an algebra

```scala
@free sealed trait DoobieM {
  def transact[A](f: ConnectionIO[A]): FS[A]
}
```

---

## Integrations

2. Implement a handler declaring the target `M[_]` and whatever restrictions it may have

```scala
implicit def freeStyleDoobieHandler[M[_]: Catchable: Suspendable]
  (implicit xa: Transactor[M]): DoobieM.Handler[M] =
      new DoobieM.Handler[M] {
        def transact[A](fa: ConnectionIO[A]): M[A] = fa.transact(xa)
      }
```

---

## Integrations

3. Optionally provide syntax for easy embedding into program's flow

```scala
implicit def freeSLiftDoobie[F[_]: DoobieM]: FreeSLift[F, ConnectionIO] =
  new FreeSLift[F, ConnectionIO] {
    def liftFSPar[A](cio: ConnectionIO[A]): FreeS.Par[F, A] = DoobieM[F].transact(cio)
  }
```

---

## Integrations

4. Use third party types interleaved with other algebras and effects

```scala
def loadUser[F[_]]
  (userId: UserId)
  (implicit 
    doobie: DoobieM[F], 
    logging: LoggingM[F]): FreeS[F, User] = {
    import doobie.implicits._
    for {
      user <- sql"SELECT * FROM User WHERE userId = $userId"
                .query[User]
                .unique
                .liftFS[F]
      - <- logging.debug(s"Loaded User: ${user.userId}")
    } yield user
}
```

---

## What's next?

- More integrations
- Better support for Tagless Final
- IntelliJ IDEA support
- Kafka
- Cassandra
- Microservice / RPC modules

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
