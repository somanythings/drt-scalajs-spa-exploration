# DRT Scala.js SPA-tutorial

## Dynamic Response Tool

A fully scala, and scalajs implementation of a tools to help understand BF resourcing requirements at airports,
based on passenger load.



## Purpose

# Overview
The tool uses akka streams interval polling to read data from various sources of flight information. To begin with, Chroma.
This is then passed through a 'workload calculator' which is fed to an algorithm provided by Home Office Science to optimise
desk resources, while achieving SLAs for queue wait times.

# The 'crunch' algorithm
Is written in R, we use Renjin to host it in the JVM

# The UI
Scalajs, with ReactJS and diode.
Scalajs so we've got one language front and back. Interop between client-server currently provided by lihaoyi's handy
autowire tool with Boopickle (binary pickler). We avoid using websockets, as they won't work on POISE machines at the time of writing this.
We use reactjs, but sadly v1, not the new beta (yet). This has a little extra cruft as the result of being evolved, rather than
designed. It will definitely be a good idea to look at using the reactjs beta.

Diode provides the immutable data model, and one way binding, which helps to ensure a sane UI developer experience.

# Scala IDE users

If you are using Scala IDE, you need to set additional settings to get your Eclipse project exported from SBT.

```
set EclipseKeys.skipParents in ThisBuild := false
eclipse
```
