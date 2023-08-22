jsmokeping
==========

A smokeping probe to use with jrds


build
-----

jsmokeping build a jsmokeping.jar, that needs to be found by jrds, as usual.

But it also needs a small setuid root binary. It's build with ant, but needs [Ant-Contrib](http://ant-contrib.sourceforge.net "Ant-Contrib") and
 also the [cpp-tasks](http://mvnrepository.com/artifact/ant-contrib/cpptasks "cpp-tasks").
The binary is build in build/smallping.

For jrds to find it, one needs to add :

    path=<installation directory>

in jrds.properties.

To make smallping usable, don't forget to set it setuid root :

    sudo chown root:root smallping
    sudo chmod 4755 smallping

or

    sudo setcap cap_net_raw+pe smallping

To use the probe, put it in any node definition and add :

    <probe type="Smokeping">
        <attr name="node">some host</attr>
    </probe>
