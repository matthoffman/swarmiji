(ns futures
  (:refer-clojure :exclude [future-call promise])
  (:import [com.google.common.util.concurrent AbstractFuture
            ListenableFuture ListenableFutureTask
            MoreExecutors Futures FutureCallback AsyncFunction SettableFuture]
           [java.util.concurrent Executor Future TimeUnit TimeoutException]))

;; decorate Clojure's normal underlying futures executor with listening future support
(def ^:private future-executor (MoreExecutors/listeningDecorator
                       clojure.lang.Agent/soloExecutor))

;; stolen from clojure source - needed to run a function with bindings (like a closure?) on another thread (i think)
(defn- binding-conveyor-fn
  [f]
  (let [frame (clojure.lang.Var/cloneThreadBindingFrame)]
    (fn
      ([]
        (clojure.lang.Var/resetThreadBindingFrame frame)
        (f))
      ([x]
        (clojure.lang.Var/resetThreadBindingFrame frame)
        (f x))
      ([x y]
        (clojure.lang.Var/resetThreadBindingFrame frame)
        (f x y))
      ([x y z]
        (clojure.lang.Var/resetThreadBindingFrame frame)
        (f x y z))
      ([x y z & args]
        (clojure.lang.Var/resetThreadBindingFrame frame)
        (apply f x y z args)))))

;; largely stolen from clojure future impl (so my futures look/feel exactly like clojure futures)
(defn- reify-future-interfaces [fut]
  (reify
    clojure.lang.IDeref
    (deref [_] (.get fut))
    clojure.lang.IBlockingDeref
    (deref
      [_ timeout-ms timeout-val]
      (try (.get fut timeout-ms TimeUnit/MILLISECONDS)
        (catch TimeoutException e
          timeout-val)))
    clojure.lang.IPending
    (isRealized [_] (.isDone fut))
    Future
    (get [_] (.get fut))
    (get [_ timeout unit] (.get fut timeout unit))
    (isCancelled [_] (.isCancelled fut))
    (isDone [_] (.isDone fut))
    (cancel [_ interrupt?] (.cancel fut interrupt?))
    ListenableFuture
    (addListener [_ listener executor] (.addListener fut listener executor))))

;; largely stolen from clojure promise impl (so they look/feel exactly like clojure futures)
(defn- reify-promises-interfaces [fut]
  (reify
    clojure.lang.IDeref
    (deref [_] (.get fut))
    clojure.lang.IBlockingDeref
    (deref
      [_ timeout-ms timeout-val]
      (try (.get fut timeout-ms TimeUnit/MILLISECONDS)
        (catch TimeoutException e
          timeout-val)))
    clojure.lang.IPending
    (isRealized [_] (.isDone fut))
    Future
    (get [_] (.get fut))
    (get [_ timeout unit] (.get fut timeout unit))
    (isCancelled [_] (.isCancelled fut))
    (isDone [_] (.isDone fut))
    (cancel [_ interrupt?] (.cancel fut interrupt?))
    clojure.lang.IFn
    (invoke
      [this x]
      (when-not (.isDone fut)
        (.set fut x)
        this))
    ListenableFuture
    (addListener [_ listener executor] (.addListener fut listener
                                         executor))))

;; TODO: i wish i knew how to DRY up the above two reify calls...

(defn- ^FutureCallback make-callback [success fail]
  (reify FutureCallback
    (onSuccess [_ result]
      (success result))
    (onFailure [_ throwable]
      (fail throwable))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; public API

(defn future-call
  ([f]
    (let [^Callable callable (cast Callable f)
          f (binding-conveyor-fn f)
          fut (.submit future-executor callable)]
      (reify-future-interfaces fut)))
  ([f success fail]
    (doto (future-call f)
      (Futures/addCallback (make-callback success fail))))
  ([f success fail ^Executor executor]
    (doto (future-call f)
      (Futures/addCallback (make-callback success fail) executor))))

(defn promise
  ([]
    (let [fut (SettableFuture/create)]
      (reify-promises-interfaces fut)))
  ([complete]
    (doto (promise)
      (Futures/addCallback (make-callback complete complete)))))

(defn add-listener!
  ([^ListenableFuture fut ^Runnable listener]
    (add-listener! fut listener (MoreExecutors/sameThreadExecutor)))
  ([^ListenableFuture fut ^Runnable listener ^Executor executor]
    (.addListener fut listener executor)))

(defn add-callback!
  ([^ListenableFuture fut success fail]
    (Futures/addCallback fut (make-callback success fail)))
  ([^ListenableFuture fut success fail ^Executor executor]
    (Futures/addCallback fut (make-callback success fail) executor)))

(defn await-futures [& futures]
  (reify-future-interfaces (Futures/allAsList futures)))

(defn map-future [f future]
  (Futures/transform
    future
    (reify
      AsyncFunction
      (apply [_ input] (future-call (fn [] (f input)))))))


;;; TEST
;(def x (map-future (partial reduce +) (apply await-futures (map #(future-call (fn [] (println (Thread/currentThread) " doing stuff...") (Thread/sleep 2000) (+ 1 2 %))) (range 0 20)))))
;
;@(apply await-futures (map #(future-call (fn [] (println (Thread/currentThread) " doing stuff...") (Thread/sleep 2000) (+ 1 2 %))) (range 0 20)))

@(future-call #(+ 1 2))


;futures=> (def p (promise #(println "got it: " %)))
;#'futures/p
;futures=> (deliver p 42)
;got it:  42
;#<futures$reify_promises_interfaces$reify__17@5fda87db: 42>
;futures=> @p
;42
