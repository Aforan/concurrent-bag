# concurrent-bag
An implementation of the Lock Free Concurrent Bag via: http://www.cse.chalmers.se/%7Etsigas/papers/Lock%20Free%20Bag%20SPAA11.pdf

Build Instructions:

This project was built in Intellij and it is recommended to build the project, however it is not required
All dependencies are located in ./lib/
All source is located in ./src/
All compiled .class files are located in ./out/production/

Manual Compilation:
(Windows)
javac -cp "lib/argparse4j-0.7.0.jar;lib/javaee-api-5.0-2.jar;lib/junit-4.0.jar;lib/log4j-1.2.17.jar;src/" src/bag/*.java src/test/*.java -d out
java -ea -cp "./lib/argparse4j-0.7.0.jar;./lib/javaee-api-5.0-2.jar;./lib/junit-4.0.jar;./lib/log4j-1.2.17.jar;./out/;./src/" test.PerformanceTest -n 4 -o 10000000 -a 0.6 -d bag

(Unix)
javac -cp "lib/argparse4j-0.7.0.jar:lib/javaee-api-5.0-2.jar:lib/junit-4.0.jar:lib/log4j-1.2.17.jar:src/" src/bag/*.java src/test/*.java -d out
java -ea -cp "./lib/argparse4j-0.7.0.jar:./lib/javaee-api-5.0-2.jar:./lib/junit-4.0.jar:./lib/log4j-1.2.17.jar:./out/:./src/" test.PerformanceTest -n 4 -o 10000000 -a 0.6 -d bag

usage: ConcurrentBag Performance
       [-h] [-n N] [-a A] [-o O] [-d D]

optional arguments:
  -h, --help             show this help message and exit
  -n N                   Number of threads
  -a A                   Add ratio out of 1.0
  -o O                   Number of operations
  -d D                   Data structure [bag|queue]