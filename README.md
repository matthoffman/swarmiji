# Swarmiji 

Swarmiji is a framework that helps in writing distributed programs using the Clojure programming language. It was written by Amit Rathore at [Runa](http://runa.com) and is still maintained at [https://github.com/runa-dev/swarmiji](https://github.com/runa-dev/swarmiji). 
This project you're looking at is a fork with somewhat different goals than Swarmiji started with. It remains to be seen whether it will make sense to fold these changes back into Swarmiji (which is, itself, a stable and production-tested project) or cast it off as its own project. I don't want to make any assumptions about what Runa will want to do, so I'm temporarily referring to this version "Vyapak Swarmiji" (which may mean ["broader"](http://dict.hinkhoj.com/words/meaning-of-vyapak-in-english.html) or ["generalized"](http://dict.hinkhoj.com/words/meaning-of-vyapak-in-english.html), or it may mean something ludicrous or insulting. I don't actually know any Hindi.)  I'll use SwarmijiV for short. 

Some of the design goals of this fork are to make Swarmiji behave a bit more like our current, in-house task management framework, and as such they don't necessarily make sense for Runa or the other existing users of Swarmiji. Some of the design goals (mainly expressed below) are to extend it to be far more general-purpose -- again, that may or may not make sense for Runa, or the other happy users of Swarmiji.  
So, we'll see. 

**NOTE: In the spirit of [README-driven development](http://tom.preston-werner.com/2010/08/23/readme-driven-development.html), this README describes many, many things that are NOT YET IMPLEMENTED.**  It is designed to sketch out what our desired end-state might look like. 

I'm intentionally referring to this particular branch as SwarmijiV instead of "Swarmiji 2", because I want to be really clear that this isn't necessarily what Runa has in mind for the next version of swarmiji, or that this design reflects an objectively _better_ version of Swarmiji. I'm calling it SwarmijiV simply because a.) it's different than the current state of Swarmiji, so it feels like I need to distinguish this "new thing" from "current state", but b.) it is starting from Swarmiji, so it feels disingenuous to call it something completely different. So, SwarmijiV it is. 

_(In practice, I wrote most of the README first, then went looking for a project that would be the closest to what I was looking for. Swarmiji was that project...so here we are.)_ 

Enough disclaimers, yet?

This design below borrows a lot from [Celery](http://celeryproject.org), a task queue in the Python world. It also borrows concepts and/or code from [die-roboter](https://github.com/technomancy/die-roboter) and [Octobot](https://github.com/cscotta/Octobot). Formal acknowledgements are in [the NOTICE file](http://github.com/...../NOTICE), but we want to give these great projects their due credit up front. 

## Goals

This branch of Swarmiji has just a few modest goals up front...

### Simple 

SwarmijiV aims to clearly separate each part of distributing a task and break those concepts down into discrete components separated by protocols. 

### Easy 

SwarmijiV aims to be easy to integrate into a current application, easy to use, and easy to understand and extend. Indeed, we want to be the distribution solution that is closest at hand for common work distribution use cases.
There are many great special-purpose data processing frameworks in the JVM ecosystem in general, and Clojure in particular:  Storm for event processing, Cascalog and the rest of the Hadoop ecosystem for data processing, as well as frameworks like Spark.  But being specifically designed to do a set of very hard problems very well, they are necessarily full-stack frameworks with hardware and software dependencies to match their problem domain. If you need to process extremely high volumes of events, Storm is a great way to go. But if you're trying to distribute work to a set of worker processes for a website, it's probably overkill. 

In the spirit of being easy to use and integrate, SwarmijiV is a library, not a framework. It aims to have very little configuration necessary. 
Of course, that also means that it doesn't handle some use cases -- like those Storm or Hadoop were designed to handle -- very well. Use the right tool for the job. 

### Fast

A single Swarmiji process can process (TODO) million tasks per minute, with (TODO)-millisecond round-trip latency using RabbitMQ, using settings optimized for small tasks. 

### Flexible

Each part of distributing work is represented by protocols, so SwarmijiV is simple to extend and customize.  Custom serializers, compression, thread pools, handlers at various points in the process, even message brokers or distribution schemes. Go crazy. Do something interesting. 

### Understandable

SwarmijiV aims to be understandable *in use* (a straightforward interface that adheres to Clojure idioms), *in design* (no magic, straightforward design), *in operation* (monitoring and maintenance commands built-in), and *in code* ([readable code makes us happy](http://link.to.marginalia)).  Note that those are lofty goals... and we're bound to miss them, if nothing else because we're fairly new to the Clojure world (so Clojure idioms don't flow naturally yet). So please let us know where we can improve. 

## Features

### Monitoring

Each producer and consumer sends out events to update the world with its status and the status of individual tasks. In addition, we integrate Yammer Metrics out of the box, so a wide variety of server-level statistics are readily available. 
TODO: consume events via Lamina?  Sounds like a 2.x  feature...

### Scaling

TODO: scaling based on available resources...

### Timeouts

Tasks can have timeouts set either globally or per-task. 

## Installation

TODO: lein, maven & gradle dep info


## Quickstart

TODO


# Design 

This is some more in-depth information about the SwarmijiV design, its components, and how to make it do what you want it to do. 

## Task Distribution and Result Stores

At its heart, SwarmijiV is intended to be not only transport-agnostic but also a distribution-algorithm-agnostic task distribution system. Meaning that the while the default implementation distributes tasks via a fairly straightforward pull-based algorithm backed by an AMQP broker, it isn't tied to that. The core protocols apply just as well to a push-based algorithm, a work-stealing algorithm, or something entirely different. They could be implemented by a system doing direct distribution via JGroups (like [in Bela Ban's paper](task-distribution.pdf) or Akka (work distribution blog post?) or ZeroMQ. 

More concretely, the system could be described this way: 
There are two actors:  a producer and a consumer.  Producers submit work (via have-work) and can wait for results via the proxy object that is returned. The consumer gets work (using get-work) and executes it, returning the result using work-result. Besides these core protocols, the producer and a consumer have two additional channels of communication: task events (information about task success and failure distributed to all nodes) and worker events (information about worker status distributed to all nodes). 

So implementations must simply have a way to express those core protocols, as well as a way to propagate events about tasks and worker status to all nodes. The system does not fundamentally assume that, for example, "add-work" will necessarily push work out to another node; it may be executed locally. It does not assume that "get-work" will necessarily pull work from a FIFO queue; it may execute work that has already been pushed specifically to this node. 

In a broker-based implementation, like the default AMQP implementation, the have-work/get-work protocols are implemented using one or more queues, return-result is implemented using a reply queue, and the events are distributed using topics (fanout exchanges, to be precise). 

Like Celery, SwarmijiV separates the concern of how a task is distributed to workers and how results are sent back. Currently, SwarmijiV supports only AMQP to both distribute work and to send results back (via a reply queue). However, we envision using JMS, JGroups, or Infinispan as distribution mechanisms and JMS, JGroups, Infinispan, Redis, memcached, Datomic, or a SQL database as potential result stores.  Feel free to help us implement them! 

## What is a task, exactly?

Like everything in SwarmijiV, there are levels. At its core, a task is just a byte stream. We hope that SwarmijiV can support clients of other languages, so we don't put any hard requirements on task payloads. 
By default, a task is just an s-expression along with some metadata telling us how the s-expression should be invoked, what we should do on failure, and so on. That metadata is merged from three places, from lowest priority to highest: static configuration, worker-specific configuration, and task-specific metadata.   

TODO: is it really a function + args, instead of an s-expression?  

This is in contrast to other task queuing systems where tasks are predefined, special-purpose entities. We have a homoiconic language, so it seems reasonable to take advantage of that. 
However, SwarmijiV also ships with a variety of helpful abstractions and helper functions to assist you in chaining together tasks, predefining tasks along with metadata, and other common patterns. Just because it's flexible doesn't mean it needs to be tedious.

Note that SwarmijiV doesn't really aim to handle distributing large sections of code and dependencies to workers. All servers are assumed to start out with identical classpaths. SwarmijiV doesn't attempt to disseminate any code beyond the task itself to other nodes, propagate classpath changes, or anything of that nature, although I suppose there's nothing stopping someone from implementing such a feature. 

## Events

Along with actually executing tasks and returning results, SwarmijiV sends out a number of events that listeners might be interested in. 

### Types of events

#### task-sent 
Sent by the task producer after a task has been placed on the queue. 

Contains the following fields: 

    task-id
    node-id  
    timestamp
    expr (text version of the expr?) OR name, args  
    retries
    queue
    exchange
    routing_key

#### task-received
Sent when the worker pulls the task off the queue. Note that it doesn't mean that the worker has begun working on the task; it could be queued up on the worker side.

Contains the following fields: 

    task-id
    node-id
    timestamp
    expr (text version of the expr?) OR name, args  
    retries

#### task-started
Sent just before the worker begins executing the task.

Contains the following fields: 

    task-id
    node-id
    timestamp
    pid?

We don't re-send the expr on this or subsequent task events because it can potentially be fairly large, and we don't want to overwhelm listeners (or bog down the broker). 

#### task-succeeded
Sent if the task has completed successfully. 

    task-id
    node-id
    timestamp
    runtime-ms
    pool-ms
    result (result-uri?)

"runtime-ms" is the time in milliseconds to actually eval the task. 
"pool-ms" is the time in milliseconds from when the task was put onto the worker pool (or handed to a worker directly, if there is no pool) and ending when the result handler callback is called. It demonstrates any overhead in the worker pool and time spent in any worker pool queue.

#### task-failed
Sent if the task failed. 

    task-id
    node-id
    timestamp
    exception
    stack-trace
    retry?

Retry is set to 'true' if the task will be retried. 

#### task-revoked
TODO: Not sure if this makes sense for us.  

### Worker Events

(to be defined) 

#### worker-online
#### worker-heartbeat
Sent every (TODO) milliseconds. if the worker has not sent a heartbeat in (TODO) minutes, it is considered to be offline.

    :hostname
    timestamp
    :freq          ; Heartbeat frequency in seconds (?)
    :sw_ident      ; ??
    :sw_ver        ; ? Software version (e.g. 0.4.6).
    :sw_sys        ; ?? Operating System (e.g. Linux, Windows, Darwin).
    :active        ; Number of currently executing tasks.
    :processed     ; Total number of tasks processed by this worker.
    .... other load information (load avg between 0 and 1, memory usage between 0 and 1, ...)

Handlers can add custom fields to the worker heartbeats. 

#### worker-offline

The worker is suspected to be dead. Every node that notices that a worker has not sent a heartbeat recently will send this, so it will be sent multiple times. 

## Routes, Queues, and Workers

When you execute a task in SwarmijiV, it is passed to a router, which decides which queue that task should be placed on.

Meanwhile, when a SwarmijiV server starts up, it uses a router (?? or just configuration?) to decide which queues it should listen on. For each queue that it is listening on, it loads one or more workers. How many workers are set in configuration (how??).
You can also scale up and down the number of workers at runtime (function, command-line, autoscaling, web UI...?)


## Task Metadata

When you submit a task, you can also submit configuration data about how that task should be executed. There are a lot of properties that you *can* specify, but they all have sensible defaults.  Defaults will be loaded first from some static, global defaults. Then we'll look at the task you're executing -- if it is a var with metadata attached, we'll look at that metadata. Properties explicitly passed into the execute function will always override defaults from either location.

Some things you can set: 

#### :max_retries
The maximum number of attempted retries before giving up. If the number of retries exceeds this value a MaxRetriesExceeded exception will be raised. NOTE: You have to call retry() manually, as it will not automatically retry on exception..

#### :default_retry_delay
#### :rate_limit
TODO: ?

#### :time_limit

The time limit for this task, in milliseconds.

#### :ignore_result

Don’t store task state. It will effectively return immediately, and always return "success" with a nil return value. 

#### :serializer-fn?
#### :compression-fn?
#### result store backend?


#### :idempotent

If set to true, messages for this task will be acknowledged after the task has been executed, not before. Currently, 'true' is the default, but that's subject to change.
Ack'ing after execution means the task may be executed twice if the worker crashes in the middle of execution, but it guarantees that it will be executed at least once. If instead we acknowledge on receipt, then it opens a window where, should the worker crash, the job may not be executed at all.


TODO: task can contain "on success" tasks?  Then "on error" tasks?  You'd get a sequential list that way.... Or just wrap that in the sexp itself?  (execute-chain task-fn1 task-fn2 task-fn3...)


## Handlers:

Tasks can register handlers (on a task, or globally?)

## Polling

??? Should we poll the queues (in priority order) like Resque (https://github.com/resque/resque/blob/1-x-stable/README.markdown) or something different?  What does Celery do? 


## Hooks

we could do hooks like Resque:
 - before a worker starts 
 - before the worker shuts down
 - before & after a pause (?)
 - before & after a job enqueue, dequeue, perform, on failure, and around perform?

Not sure if this is really idomatic for a Clojure app...


## Small Tasks vs. Large Tasks

The AMQP backend, specifically, can prefetch tasks so that there is always work available. 
The workers’ default prefetch count is the (TODO: prefetch multiplier) setting multiplied by the number of workers listening on that queue.

If you’re doing mostly I/O you may want a relatively high worker count, but if mostly CPU-bound, try to keep it close to the number of CPUs on your machine. The default is the number of cores on your machine, as reported by the JVM. 

If you have many tasks with a long duration you want the multiplier value to be 1, which means it will only reserve one task per worker process at a time.

However – If you have many short-running tasks, and throughput/round trip latency is important to you, this number should be large. The worker is able to process more tasks per second if the messages have already been prefetched, and is available in memory. You may have to experiment to find the best value that works for you. Values like 50 or 150 might make sense in these circumstances. Say 64, or 128.

If you have a combination of long- and short-running tasks, the best option is to use two worker nodes that are configured separately, and route the tasks according to the run-time. (see Routing Tasks).


## Retrying

When executing a task, the current task function and a variety of metadata about the task (its task-id, start time, and so on) are attached to a dynamic binding called TODO. If that task fails, then we'll look in that task metadata to see if there's a retry handler available. If so, we'll execute it. 
If you want to manually retry, call the (retry) function. It will re-execute the task that is currently bound. 

When you call retry it will send a new message, using the same task-id, and it will take care to make sure the message is delivered to the same queue as the originating task.
Every retry is a new message with the same task UUID, and a retry count is incremented.

If the task calls (retry) and the limit is exceeded it raises the exception.

Using a custom retry delay

When a task is to be retried, it can wait for a given amount of time before doing so, and the default delay is defined by the :retry_delay attribute. By default this is (TODO?)By default this is set to (TODO?).

You can also provide the countdown argument to (retry) to override this default.


Resque plugin also supports retry, delay and exponential backoff...


# Calling Tasks

## Call with a chain:

(execute-task-chain task1, task2, task3)
 This will execute task1, then (if successful) execute task2, then (if successful) execute task3.
 We can also express this in a map:

 (execute-task-map
     { task1  { :on-success { task2 { :on-success task3 } }
                :on-error [retry 3 exponential-backoff 1000 1]}}
TODO: check how lamina expresses that.

Celery supports linking tasks together so that one task follows another. The callback task will be applied with the result of the parent task as a partial argument:


http://docs.celeryproject.org/en/latest/userguide/canvas.html  for some ideas on how to group tasks together...


## Call with explicit queue name (supported? Too queue-specific? )

# Revoking tasks

All worker nodes keeps a list in memory of revoked task ids. 

When a worker receives a revoke request it will skip executing the task. It will not terminate a running task.

# FAQ

## Why not just throw s-expressions on a RabbitMQ queue and be done with it? 

For a long time, the recommendations on Clojure mailing lists or SO posts was exactly that: "Really, task distribution in Clojure is so trivial there's no point in using a library."  Alternately, someone would throw in "you should use Storm or Cascalog". Now, we heartily recommend Storm or Cascalog if that's what you need, but with all due respect we think that saying that task distribution is trivial is a bit naive. Sure, Clojure is homoiconic, so sending the actual executable unit over the wire is not complicated. However, dealing with errors, handling results, implementing timeouts, or any of the other features you're likely to need in a production system are not trivial. Not rocket science, but not trivial. If you want to reimplement them, go ahead...we happen to think that writing these things is fun.  But we also think you shouldn't have to. 

## Why not just implement a Celery backend? 
That's a good question. We considered it.  ...

There's some overhead in having it strictly compatible with Celery, and it has no compelling use case for us. I do wonder just how hard compatibility would be, though...


## Disclaimer and Notice

Some blocks of text are from Celery's user manual, by Ask Solem & contributors. 
This README is currently licensed Creative Commons Attribution-Noncommercial-Share Alike 3.0 
