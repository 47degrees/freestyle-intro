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

## Goals

- Approachable to newcomers <!-- .element: class="fragment" -->
- Stack-safe <!-- .element: class="fragment" -->
- Dead simple integrations with Scala's library ecosystem <!-- .element: class="fragment" -->

---

## Interface + Impl driven design

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

## Declaration Boilerplate Reduction

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

## Composition boilerplate reduction

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
- Provide implicit implementations of each algebra `Handler` <!-- .element: class="fragment" -->
- Run your programs at the edge of the world <!-- .element: class="fragment" -->

---

## Predictable Workflow

Declare your algebras

```scala
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

/* Represents persistence operations */
@free trait Repository {
  def addCat(a: String): FS[Unit]
  def getAllCats : FS[List[String]]
}
```

---

## Predictable Workflow

Combine your algebras in arbitrarily nested modules

```scala
/* Handles user interaction */
@module trait Persistence {
  val validation: Validation
  val repository: Repository
}

@module trait App {
  val interact: Interact
  val errorM : ErrorM
  val persistence: Persistence
}
```

---

## Predictable Workflow

Declare and compose programs

```scala
def program[F[_]: Interacts : Repository: ErrorM]: FreeS[F, Unit] = {
  val I = Interacts[F]
  val R = Repository[F]
  val E = ErrorM[F]
  for {
    cat <- I.ask("What's the kitty's name?")
    isValid <- (minSize(cat, 10) |@| hasNumber(cat)).map(_ && _) //may run ops in parallel
    _ <- if (isValid) R.addCat(cat) else E.error(new RuntimeException("invalid name!"))
    cats <- R.getAllCats
    _ <- I.tell(cats.toString)
  } yield ()
}
```

---

## Predictable Workflow

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

## Predictable Workflow

Run your program to your desired `M[_]`

```scala
program[App.Op].interpret[Task]
```

---

## Effects

An alternative to monad transformers

- error: Signal errors <!-- .element: class="fragment" -->
- either: Flatten `Right` / short-circuit `Left` <!-- .element: class="fragment" -->
- option: Flatten `Some` / short-circuit on `None` <!-- .element: class="fragment" -->
- reader: Deffer dependency injection until program interpretation <!-- .element: class="fragment" -->
- writer: Log / Accumulate values <!-- .element: class="fragment" -->
- state: Pure functional state threaded over the program monadic sequence <!-- .element: class="fragment" -->
- traverse: Generators over `Foldable` and `Traversable` <!-- .element: class="fragment" -->
- validation: Accumulate and inspect errors throughout the monadic sequence <!-- .element: class="fragment" -->
- async: Integrate with callback based API's <!-- .element: class="fragment" -->

---

## Effects

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

## Effects

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

## Effects

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

## Integrations

- Monix: Target runtime and `async` effect integration. <!-- .element: class="fragment" -->
- Fetch: Algebra to run fetch instances + Auto syntax `Fetch -> FS`. <!-- .element: class="fragment" -->
- FS2: Embed FS2 `Stream` in Freestyle programs. <!-- .element: class="fragment" -->
- Doobie: Embed `ConnectionIO` programs into Freestyle. <!-- .element: class="fragment" -->
- Slick: Embed `DBIO` programs into Freestyle. <!-- .element: class="fragment" -->
- Akka Http: `EntityMarshaller`s to return Freestyle programs in Akka-Http endpoints. <!-- .element: class="fragment" -->
- Play: Implicit conversions to return Freestyle programs in Play Actions. <!-- .element: class="fragment" -->
- Twitter Util: `Capture` instances for Twitter's `Future` & `Try`. <!-- .element: class="fragment" -->
- Finch: Mapper instances to return Freestyle programs in Finch endpoints. <!-- .element: class="fragment" -->
- Http4s: `EntityEncoder` instance to return Freestyle programs in Http4S endpoints. <!-- .element: class="fragment" -->

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
