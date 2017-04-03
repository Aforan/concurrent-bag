echo "runtests.bat nthreads operations addratio datastructure
echo "Begginning Performance Tests"

set CLASSPATH=%~dp0out\production\concurrent-bag\;%~dp0lib\argparse4j-0.7.0.jar;%~dp0lib\javee-api-5.0-2.jar;%~dp0lib\junit-4.0.jar;%~dp0lib\log4j-1.2.17.jar

java test.PerformanceTest -n 2 -o %2 -a %3 -d %4

for /l %%x in (4, 2, %1) do java test.PerformanceTest -n %%x -o %2 -a %3 -d %4
