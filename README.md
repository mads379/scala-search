Next Scala Search
=================

The next Scala Search Engine for Scala IDE. Currently there's not much
to see here.

Building & Installing
---------------------

The build is configured using maven. To build and install the the plugin invoke the following
maven command>

    mvn -P scala-ide-master-scala-trunk clean package

This will generate a local update site so you can install it as you would with any another
other Eclipse plugin. You click the following menus `Help > Install New Software`, click on Add, then Local and select the `org.scala.tools.eclipse.search.update-site/target/site/`

Tests
-----

If you want to run the tests in Eclipse you can follow the set-up guide outline
[here](http://scala-ide.org/docs/dev/testing/eclipse-tests.html)

    
Running it
----------

The easiest way to work on the plugin is to import the projects into Eclipse and run it using 
the [Equinox Weaving Launcher](https://github.com/milessabin/equinox-weaving-launcher) plugin.
To install the Equinox Weaving Launcher, use the following Eclipse update site:

[http://www.chuusai.com/eclipse/equinox-weaving-launcher/](http://www.chuusai.com/eclipse/equinox-weaving-launcher/)

This adds the run configuration `Eclipse Application with Equinox Weaving`.

Links
-----

- [Jenkins Job](https://jenkins.scala-ide.org:8496/jenkins/job/scala-search-nightly-2.1-2.10/)