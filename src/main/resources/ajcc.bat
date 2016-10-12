@echo off

rem An example how to run Clover AspectJ Compiler:
rem - you have to provide org.aspectj:aspectjrt and org.aspectj:aspectjtools JARs
rem - you have to provide com.atlassian.clover:clover-aspectj-compiler and com.atlassian.clover:clover JARs
rem - you can pass the same arguments for ajcc.sh as for ajc (https://eclipse.org/aspectj/doc/next/devguide/ajc-ref.html)
rem - you can also pass Clover-related settings, such as '--initstring clover.db' or '--instrlevel method/statement'

java -cp "clover-aspectj-compiler-0.8.0.jar:clover-4.1.2.jar:aspectjrt-1.8.9.jar:aspectjtools-1.8.9.jar" com.atlassian.clover.instr.aspectj.CloverAjc "$@"
